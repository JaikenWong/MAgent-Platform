package com.magent.platform.service.approval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.mapper.ApprovalMapper;
import com.magent.platform.mapper.ApprovalPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批超时检查: 定时扫描 pending 审批, 按 policy.timeoutAction 处理.
 *  auto_reject -> 自动拒绝 (cancel task)
 *  escalate   -> 重推通知后拒绝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTimeoutService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 1800;

    private final ApprovalMapper approvalMapper;
    private final ApprovalPolicyMapper policyMapper;
    private final ApprovalEngine engine;
    private final ApprovalNotifier notifier;

    /** 每 60s 扫一次超时审批. */
    @Scheduled(fixedDelay = 60_000)
    public void checkTimeouts() {
        List<Approval> pending = approvalMapper.selectList(
            new QueryWrapper<Approval>().eq("status", "pending"));
        if (pending.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (Approval a : pending) {
            if (a.getCreatedAt() == null) continue;
            ApprovalPolicy policy = a.getPolicyId() == null ? null : policyMapper.selectById(a.getPolicyId());
            int timeoutSec = (policy != null && policy.getTimeoutSeconds() != null)
                ? policy.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
            if (a.getCreatedAt().plusSeconds(timeoutSec).isBefore(now)) {
                handleTimeout(a, policy);
            }
        }
    }

    private void handleTimeout(Approval a, ApprovalPolicy policy) {
        String action = policy != null && policy.getTimeoutAction() != null
            ? policy.getTimeoutAction() : "auto_reject";
        log.warn("[Approval] 审批超时: approval={} task={} action={}", a.getId(), a.getTaskId(), action);
        try {
            if ("escalate".equals(action)) {
                notifier.notifyApproval(a, policy);
            }
            engine.decide(a.getId(), false, "审批超时自动拒绝 (" + action + ")", "system", "timeout");
            notifier.pushPendingCount(engine.pendingCount());
        } catch (Exception e) {
            log.error("[Approval] 超时处理失败: approval={}", a.getId(), e);
        }
    }
}
