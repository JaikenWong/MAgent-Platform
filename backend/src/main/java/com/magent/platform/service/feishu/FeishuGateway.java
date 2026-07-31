package com.magent.platform.service.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.entity.Agent;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import com.magent.platform.service.orchestrator.AggregatorService;
import com.magent.platform.service.orchestrator.ExecutorService;
import com.magent.platform.service.orchestrator.PlannerService;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 飞书网关: 事件路由 + 消息处理入口, 连接飞书 ↔ Orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuGateway {

    private final FeishuBotMapper botMapper;
    private final FeishuClient feishuClient;
    private final PlannerService planner;
    private final ExecutorService executorService;
    private final AggregatorService aggregator;
    private final ObjectMapper om;

    /**
     * 处理 URL 验证 (飞书订阅时的 challenge).
     */
    public String verifyUrl(String botId, Map<String, Object> body) {
        String type = (String) body.get("type");
        if ("url_verification".equals(type)) {
            return (String) body.get("challenge");
        }
        return null;
    }

    /**
     * 处理飞书事件回调.
     */
    public void handleEvent(String botId, Map<String, Object> body) {
        FeishuBot bot = loadBot(botId);
        if (bot == null) {
            log.warn("unknown bot: {}", botId);
            return;
        }

        String token = feishuClient.getTenantToken(bot.getAppId(), decryptSecret(bot.getAppSecret()));

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) body.get("event");
        if (event == null) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) body.get("header");
        String eventType = header != null ? (String) header.get("event_type") : null;
        if (!"im.message.receive_v1".equals(eventType)) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> eventBody = (Map<String, Object>) event.get("event");
        if (eventBody == null) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) eventBody.get("message");
        if (message == null) return;

        String msgType = (String) message.get("msg_type");
        String chatId = (String) message.get("chat_id");
        String text = extractText(message);

        if (text == null || text.isBlank()) return;

        // Route to orchestrator
        String contextId = UUID.randomUUID().toString();
        ExecutionPlan plan = planner.plan(text);
        log.info("feishu plan: mode={} stages={}", plan.executionMode(), plan.stages().size());

        List<Map<String, String>> results = executorService.execute(plan, contextId);
        String reply = aggregator.aggregate(results, plan, text);

        // Send reply via Feishu
        feishuClient.sendText(token, chatId, reply);
    }

    public void handleCardCallback(String botId, Map<String, Object> body) {
        // Phase 4: approval card button callbacks
        log.info("card callback received for bot {}: {}", botId, body);
    }

    private FeishuBot loadBot(String botId) {
        return botMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FeishuBot>()
                .eq("id", botId)
                .eq("status", "active"));
    }

    private String extractText(Map<String, Object> message) {
        String msgType = (String) message.get("msg_type");
        if ("text".equals(msgType)) {
            return (String) message.get("text");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) message.get("content");
        if (content != null) {
            return (String) content.get("text");
        }
        return null;
    }

    private String decryptSecret(String secret) {
        try { return CryptoUtil.decrypt(secret); } catch (Exception e) {
            return secret; // fallback plaintext
        }
    }
}