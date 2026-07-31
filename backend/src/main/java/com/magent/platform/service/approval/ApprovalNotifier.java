package com.magent.platform.service.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.entity.Approval;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import com.magent.platform.service.feishu.FeishuClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审批通知: 飞书卡片 + WebSocket 推送.
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
     * 推送审批到飞书卡片 (给管理员).
     */
    public void notifyFeishu(Approval approval, String adminFeishuUserId) {
        try {
            // Use first active bot for sending
            FeishuBot bot = botMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FeishuBot>()
                    .eq("status", "active").last("limit 1"));
            if (bot == null) {
                log.warn("no active feishu bot for approval notification");
                return;
            }

            String token = feishuClient.getTenantToken(bot.getAppId(), decryptSecret(bot.getAppSecret()));

            Map<String, Object> card = Map.of(
                "config", Map.of("wide_screen_mode", true),
                "header", Map.of("title", Map.of("tag", "plain_text", "content", "审批请求")),
                "elements", java.util.List.of(
                    Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content",
                        "**操作**: " + approval.getSkillName() + "\n**Task**: " + approval.getTaskId() + "\n**Payload**: " +
                        (approval.getPayload() != null ? approval.getPayload() : "")))
                )
            );

            // Send card message to admin user
            String json = om.writeValueAsString(Map.of(
                "receive_id", adminFeishuUserId,
                "msg_type", "interactive",
                "content", om.valueToTree(card).toString()
            ));
            feishuClient.sendText(token, adminFeishuUserId, json);

        } catch (Exception e) {
            log.error("failed to send feishu approval notification", e);
        }
    }

    /**
     * WebSocket 推送待审批数量到前端.
     */
    public void pushPendingCount(int count) {
        messagingTemplate.convertAndSend("/topic/approvals", Map.of("pendingCount", count));
    }

    private String decryptSecret(String secret) {
        try { return CryptoUtil.decrypt(secret); } catch (Exception e) { return secret; }
    }
}