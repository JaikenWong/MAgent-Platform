package com.magent.platform.service.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.ApprovalPolicy;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import com.magent.platform.service.feishu.FeishuClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审批通知: 飞书互动卡片 (带批准/拒绝按钮) + WebSocket 推送.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalNotifier {

    private final FeishuClient feishuClient;
    private final FeishuBotMapper botMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper om;

    /**
     * 推送审批到飞书 escalation 群 (带按钮), 无 escalation chat 则跳过卡片.
     */
    public void notifyApproval(Approval approval, ApprovalPolicy policy) {
        String chatId = resolveEscalationChat(policy);
        if (chatId == null) {
            log.info("[Approval] 无飞书 escalation chat, 跳过卡片: approval={}", approval.getId());
            return;
        }
        try {
            FeishuBot bot = firstActiveBot();
            if (bot == null) {
                log.warn("[Approval] 无 active bot, 跳过卡片");
                return;
            }
            String token = feishuClient.getTenantToken(bot.getAppId(), decrypt(bot.getAppSecret()));
            feishuClient.sendCard(token, chatId, buildApprovalCard(approval));
            log.info("[Approval] 审批卡片已发: approval={} chat={}", approval.getId(), chatId);
        } catch (Exception e) {
            log.error("[Approval] 发送审批卡片失败: approval={}", approval.getId(), e);
        }
    }

    /** WebSocket 推送待审批数量到前端角标. */
    public void pushPendingCount(int count) {
        messagingTemplate.convertAndSend("/topic/approvals", Map.of("pendingCount", count));
    }

    /** 飞书互动卡片: 操作详情 + 批准/拒绝按钮 (value 含 approvalId + action). */
    private Map<String, Object> buildApprovalCard(Approval approval) {
        return Map.of(
            "config", Map.of("wide_screen_mode", true),
            "header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "审批请求: " + approval.getSkillName()),
                "template", "blue"),
            "elements", List.of(
                Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content",
                    "**Task**: " + approval.getTaskId()
                    + "\n**操作**: " + approval.getSkillName()
                    + "\n**详情**: " + (approval.getPayload() != null ? approval.getPayload() : "-"))),
                Map.of("tag", "action", "actions", List.of(
                    Map.of("tag", "button",
                        "text", Map.of("tag", "plain_text", "content", "批准"),
                        "type", "primary",
                        "value", Map.of("approvalId", approval.getId(), "action", "approve")),
                    Map.of("tag", "button",
                        "text", Map.of("tag", "plain_text", "content", "拒绝"),
                        "type", "danger",
                        "value", Map.of("approvalId", approval.getId(), "action", "reject"))
                ))
            )
        );
    }

    @SuppressWarnings("unchecked")
    private String resolveEscalationChat(ApprovalPolicy policy) {
        if (policy == null || policy.getEscalationChannel() == null) return null;
        try {
            Map<String, Object> ch = om.readValue(policy.getEscalationChannel(), Map.class);
            return (String) ch.get("feishu_chat_id");
        } catch (Exception e) {
            return null;
        }
    }

    private FeishuBot firstActiveBot() {
        return botMapper.selectOne(
            new QueryWrapper<FeishuBot>().eq("status", "active").last("limit 1"));
    }

    private String decrypt(String cipher) {
        try { return CryptoUtil.decrypt(cipher); } catch (Exception e) { return cipher; }
    }
}
