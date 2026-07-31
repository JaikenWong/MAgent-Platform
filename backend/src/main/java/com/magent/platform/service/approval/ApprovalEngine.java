package com.magent.platform.service.approval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.mapper.ApprovalMapper;
import com.magent.platform.mapper.ApprovalPolicyMapper;
import com.magent.platform.service.a2a.TaskManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批引擎: 策略匹配 + 审批创建 + 决策执行.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine {

    private final ApprovalPolicyMapper policyMapper;
    private final ApprovalMapper approvalMapper;
    private final TaskManagerService taskManagerService;
    private final ApprovalNotifier notifier;
    private final ObjectMapper om;

    /**
     * 请求审批: 检查是否需要审批, 如需要则创建审批记录并挂起 Task.
     * @return true if approval is required (task suspended), false if auto-approved
     */
    public boolean requestApproval(String taskId, String agentId, String skillName, Map<String, Object> payload) {
        // 1. Find matching policies
        List<ApprovalPolicy> policies = findPolicies(agentId, skillName);
        if (policies.isEmpty()) return false; // no policy => auto-approve

        ApprovalPolicy policy = policies.get(0);
        if ("auto".equals(policy.getStrategy())) return false; // auto approve

        // 2. Create approval record
        Approval approval = new Approval();
        approval.setTaskId(taskId);
        approval.setPolicyId(policy.getId());
        approval.setRequestedBy(agentId);
        approval.setSkillName(skillName);
        approval.setPayload(serializePayload(payload));
        approval.setStatus("pending");
        approval.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(approval);

        // 3. If "notify" only, just log and return (task continues)
        if ("notify".equals(policy.getStrategy())) {
            log.info("approval notify only: task={} skill={}", taskId, skillName);
            approval.setStatus("notified");
            approvalMapper.updateById(approval);
            return false;
        }

        // 4. Suspend task
        taskManagerService.inputRequired(taskId,
            new Message("system", java.util.List.of(new TextPart("审批中: " + skillName + " — 等待管理员审批")),
                null, null, null, null));
        log.info("approval created: id={} task={} policy={}", approval.getId(), taskId, policy.getName());
        notifier.notifyApproval(approval, policy);
        notifier.pushPendingCount(pendingCount());
        return true;
    }

    /**
     * 执行审批决定.
     */
    public void decide(String approvalId, boolean approved, String comment, String adminId, String channel) {
        Approval approval = approvalMapper.selectById(approvalId);
        if (approval == null) throw new BizException(404, "审批记录不存在");
        if (!"pending".equals(approval.getStatus())) throw new BizException(409, "审批已处理");

        // Check policy quorum
        ApprovalPolicy policy = policyMapper.selectById(approval.getPolicyId());
        if (approved && "require_quorum".equals(policy.getStrategy()) && policy.getQuorum() != null) {
            long alreadyApproved = approvalMapper.selectCount(
                new QueryWrapper<Approval>()
                    .eq("task_id", approval.getTaskId())
                    .eq("status", "approved"));
            if (alreadyApproved < policy.getQuorum() - 1) {
                log.info("quorum not met yet: {}/{} for task {}", alreadyApproved + 1, policy.getQuorum(), approval.getTaskId());
                approval.setStatus("approved");
                approval.setDecisionBy(adminId);
                approval.setDecisionAt(LocalDateTime.now());
                approval.setComment(comment);
                approval.setDecisionChannel(channel);
                approvalMapper.updateById(approval);
                return; // wait for more
            }
        }

        // Update approval
        approval.setStatus(approved ? "approved" : "rejected");
        approval.setDecisionBy(adminId);
        approval.setDecisionAt(LocalDateTime.now());
        approval.setComment(comment);
        approval.setDecisionChannel(channel);
        approvalMapper.updateById(approval);

        // Resume or cancel task
        if (approved) {
            taskManagerService.working(approval.getTaskId());
            log.info("task resumed after approval: {}", approval.getTaskId());
        } else {
            taskManagerService.finish(approval.getTaskId(), TaskState.CANCELED,
                "审批被拒绝: " + (comment != null ? comment : ""), null);
            log.info("task canceled after rejection: {}", approval.getTaskId());
        }
    }

    public int pendingCount() {
        return approvalMapper.selectCount(
            new QueryWrapper<Approval>().eq("status", "pending")).intValue();
    }

    public List<Approval> findPending() {
        return approvalMapper.selectList(
            new QueryWrapper<Approval>().eq("status", "pending").orderByAsc("created_at"));
    }

    private List<ApprovalPolicy> findPolicies(String agentId, String skillName) {
        return policyMapper.selectList(
            new QueryWrapper<ApprovalPolicy>()
                .eq("enabled", true)
                .and(w -> w.isNull("applies_to")
                    .or().like("applies_to", skillName)));
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return om.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}