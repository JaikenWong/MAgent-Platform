package com.magent.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.mapper.ApprovalMapper;
import com.magent.platform.mapper.ApprovalPolicyMapper;
import com.magent.platform.service.a2a.TaskManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批超时检查: 定时扫描过期审批, 执行 timeout_action.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutTask {

    private final ApprovalMapper approvalMapper;
    private final ApprovalPolicyMapper policyMapper;
    private final TaskManagerService taskManagerService;

    @Scheduled(fixedRate = 60_000) // every 60s
    public void checkTimeouts() {
        List<Approval> pending = approvalMapper.selectList(
            new QueryWrapper<Approval>().eq("status", "pending"));

        for (Approval approval : pending) {
            ApprovalPolicy policy = policyMapper.selectById(approval.getPolicyId());
            if (policy == null || policy.getTimeoutSeconds() == null) continue;

            LocalDateTime deadline = approval.getCreatedAt().plusSeconds(policy.getTimeoutSeconds());
            if (LocalDateTime.now().isBefore(deadline)) continue;

            // Timeout!
            String action = policy.getTimeoutAction();
            log.warn("approval {} timed out, action={}", approval.getId(), action);

            approval.setStatus("expired");
            approval.setComment("超时自动" + ("auto_reject".equals(action) ? "拒绝" : "升级"));
            approval.setDecisionChannel("timeout");
            approval.setDecisionAt(LocalDateTime.now());
            approvalMapper.updateById(approval);

            if ("auto_reject".equals(action)) {
                taskManagerService.finish(approval.getTaskId(), TaskState.CANCELED,
                    "审批超时自动拒绝", null);
            }
            // escalate: just log for now, Phase 5 can add escalation channels
        }
    }
}