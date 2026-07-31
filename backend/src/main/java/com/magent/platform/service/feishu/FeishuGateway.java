package com.magent.platform.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.service.approval.ApprovalEngine;
import com.magent.platform.service.approval.ApprovalNotifier;
import com.magent.platform.service.orchestrator.AggregatorService;
import com.magent.platform.service.orchestrator.ExecutorService;
import com.magent.platform.service.orchestrator.PlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书网关: 长连接消息事件 -> Orchestrator -> 飞书回复.
 *
 *  事件由 SDK 长连接接收 (FeishuLongConnectionService 已在 relayExecutor 异步调度),
 *  此处 handleMessageEvent 同步执行规划/执行/聚合/回复.
 *  卡片按钮回调走 HTTP webhook -> handleCardCallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuGateway {

    private final FeishuClient feishuClient;
    private final PlannerService planner;
    private final ExecutorService executorService;
    private final AggregatorService aggregator;
    private final ApprovalEngine approvalEngine;
    private final ApprovalNotifier approvalNotifier;
    private final ObjectMapper om;

    /** 消息去重: 飞书可能重复推送同一事件. */
    private final ConcurrentHashMap<String, Boolean> processedMessages = new ConcurrentHashMap<>();

    // ───── 长连接消息事件 ─────

    public void handleMessageEvent(FeishuBot bot, P2MessageReceiveV1 event) {
        EventMessage msg = event.getEvent().getMessage();
        String messageId = msg.getMessageId();

        if (processedMessages.putIfAbsent(messageId, Boolean.TRUE) != null) {
            log.debug("[FeishuGW] 消息已处理, 跳过: {}", messageId);
            return;
        }
        if (processedMessages.size() > 10_000) processedMessages.clear();

        String chatId = msg.getChatId();
        String text = extractText(msg.getMessageType(), msg.getContent());
        if (text == null || text.isBlank()) {
            log.info("[FeishuGW] 无可处理文本: bot={} type={}", bot.getName(), msg.getMessageType());
            return;
        }
        // 去掉 @机器人 mention 占位
        text = text.replaceAll("@_user_\\d+", "").trim();
        if (text.isBlank()) return;

        log.info("[FeishuGW] 收到消息: bot={} chat={} len={}", bot.getName(), chatId, text.length());
        processMessage(bot, chatId, text);
    }

    private void processMessage(FeishuBot bot, String chatId, String text) {
        try {
            String token = feishuClient.getTenantToken(bot.getAppId(), decrypt(bot.getAppSecret()));
            String contextId = UUID.randomUUID().toString();

            ExecutionPlan plan = planner.plan(text);
            log.info("[FeishuGW] 规划: mode={} stages={}", plan.executionMode(), plan.stages().size());

            List<Map<String, String>> results = executorService.execute(plan, contextId);
            String reply = aggregator.aggregate(results, plan, text);

            feishuClient.sendText(token, chatId, reply);
        } catch (Exception e) {
            log.error("[FeishuGW] 处理消息失败 bot={}", bot.getName(), e);
            try {
                String token = feishuClient.getTenantToken(bot.getAppId(), decrypt(bot.getAppSecret()));
                feishuClient.sendText(token, chatId, "处理失败: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    // ───── 审批卡片按钮回调 (HTTP webhook) ─────

    public void handleCardCallback(String botId, Map<String, Object> body) {
        // 飞书互动卡片按钮回调: body.action.value = {approvalId, action}
        @SuppressWarnings("unchecked")
        Map<String, Object> action = (Map<String, Object>) body.get("action");
        if (action == null) {
            log.info("[FeishuGW] 卡片回调无 action: botId={}", botId);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) action.get("value");
        if (value == null) return;

        String approvalId = (String) value.get("approvalId");
        String actionType = (String) value.get("action");
        if (approvalId == null || actionType == null) {
            log.warn("[FeishuGW] 卡片回调缺 approvalId/action: value={}", value);
            return;
        }
        String openId = (String) body.get("open_id");
        boolean approved = "approve".equals(actionType);
        log.info("[FeishuGW] 审批决策: approval={} action={} by={}", approvalId, actionType, openId);
        try {
            approvalEngine.decide(approvalId, approved, null, openId, "feishu");
            approvalNotifier.pushPendingCount(approvalEngine.pendingCount());
        } catch (Exception e) {
            log.error("[FeishuGW] 审批决策失败: approval={}", approvalId, e);
        }
    }

    // ───── helpers ─────

    /**
     * 从飞书 content JSON 字符串提取文本.
     *  text: {"text":"实际内容"}
     *  post: {"title":...,"content":[[{tag:"text","text":"..."}]]}
     */
    private String extractText(String msgType, String content) {
        if (content == null || content.isBlank()) return null;
        try {
            JsonNode node = om.readTree(content);
            if ("text".equals(msgType)) {
                String t = node.path("text").asText("");
                return t.isBlank() ? null : t;
            }
            if ("post".equals(msgType)) {
                return extractPostText(node);
            }
            log.debug("[FeishuGW] 不支持的消息类型: {}", msgType);
            return null;
        } catch (Exception e) {
            log.warn("[FeishuGW] 解析消息内容失败: type={} content={}", msgType, content, e);
            return null;
        }
    }

    private String extractPostText(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        JsonNode title = content.path("title");
        if (title.isTextual() && !title.asText().isBlank()) {
            sb.append(title.asText()).append("\n");
        }
        JsonNode body = content.path("content");
        if (body.isArray()) {
            for (JsonNode para : body) {
                if (!para.isArray()) continue;
                for (JsonNode el : para) {
                    if ("text".equals(el.path("tag").asText())) {
                        sb.append(el.path("text").asText(""));
                    }
                }
                sb.append("\n");
            }
        }
        String result = sb.toString().trim();
        return result.isBlank() ? null : result;
    }

    private String decrypt(String cipher) {
        if (cipher == null || cipher.isBlank()) return "";
        try { return CryptoUtil.decrypt(cipher); } catch (Exception e) { return cipher; }
    }
}
