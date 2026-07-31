package com.magent.platform.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.R;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.entity.Conversation;
import com.magent.platform.entity.Message;
import com.magent.platform.mapper.ConversationMapper;
import com.magent.platform.mapper.MessageMapper;
import com.magent.platform.service.orchestrator.AggregatorService;
import com.magent.platform.service.orchestrator.ExecutorService;
import com.magent.platform.service.orchestrator.PlannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Orchestrator API: 用户入口, 驱动多 Agent 协同.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

    private final PlannerService planner;
    private final ExecutorService executorService;
    private final AggregatorService aggregator;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper om;
    private final Executor threadPool;

    public OrchestratorController(PlannerService planner, ExecutorService executorService,
            AggregatorService aggregator, ConversationMapper conversationMapper,
            MessageMapper messageMapper, ObjectMapper om,
            @Qualifier("agentExecutor") Executor threadPool) {
        this.planner = planner;
        this.executorService = executorService;
        this.aggregator = aggregator;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.om = om;
        this.threadPool = threadPool;
    }

    @PostMapping("/chat")
    public R<ChatResult> chat(@RequestBody ChatRequest req) {
        String userMessage = req.message();
        if (userMessage == null || userMessage.isBlank()) {
            return R.fail("message is required");
        }

        // 1. Create or reuse conversation
        Conversation convo = resolveConversation(req.conversationId(), "web");

        // 2. Save user message
        saveMessage(convo.getId(), "user", userMessage, null);

        // 3. Plan
        ExecutionPlan plan = planner.plan(userMessage);
        log.info("execution plan: mode={} stages={}", plan.executionMode(), plan.stages().size());

        // 4. Execute
        List<Map<String, String>> agentResults = executorService.execute(plan, convo.getA2aContextId());
        for (Map<String, String> r : agentResults) {
            saveMessage(convo.getId(), "agent", r.get("output"), r.get("agentId"));
        }

        // 5. Aggregate
        String finalReply = aggregator.aggregate(agentResults, plan, userMessage);

        // 6. Save orchestrator message
        saveMessage(convo.getId(), "orchestrator", finalReply, null);

        return R.ok(new ChatResult(convo.getId(), finalReply, plan.executionMode(), plan.stages(), plan.reasoning()));
    }

    @GetMapping("/chat/stream")
    public SseEmitter chatStream(@RequestParam String message, @RequestParam(required = false) String conversationId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min

        threadPool.execute(() -> {
            try {
                Conversation convo = resolveConversation(conversationId, "web");

                // user message
                saveMessage(convo.getId(), "user", message, null);
                sendEvent(emitter, "status", "planning");

                // plan
                ExecutionPlan plan = planner.plan(message);
                sendEvent(emitter, "plan", Map.of("mode", plan.executionMode(), "stages", plan.stages(), "reasoning", plan.reasoning()));

                // execute with streaming
                sendEvent(emitter, "status", "executing");

                List<Map<String, String>> results = executorService.execute(plan, convo.getA2aContextId());
                for (Map<String, String> r : results) {
                    sendEvent(emitter, "agent_response", r);
                    saveMessage(convo.getId(), "agent", r.get("output"), r.get("agentId"));
                }

                // aggregate
                sendEvent(emitter, "status", "aggregating");
                String reply = aggregator.aggregate(results, plan, message);
                saveMessage(convo.getId(), "orchestrator", reply, null);

                sendEvent(emitter, "done", Map.of("conversationId", convo.getId(), "reply", reply));
                emitter.complete();
            } catch (Exception e) {
                log.error("orchestrator stream error", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private Conversation resolveConversation(String conversationId, String source) {
        if (conversationId != null && !conversationId.isBlank()) {
            Conversation existing = conversationMapper.selectById(conversationId);
            if (existing != null) return existing;
        }
        Conversation convo = new Conversation();
        convo.setSource(source);
        convo.setA2aContextId(UUID.randomUUID().toString());
        convo.setStatus("active");
        convo.setCreatedAt(LocalDateTime.now());
        convo.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(convo);
        return convo;
    }

    private void saveMessage(String conversationId, String role, String content, String agentId) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setAgentId(agentId);
        msg.setParts("[{\"type\":\"text\",\"text\":\"" + escapeJson(content) + "\"}]");
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("failed to send SSE event {}: {}", event, e.getMessage());
        }
    }

    public record ChatRequest(String message, String conversationId) {}
    public record ChatResult(String conversationId, String reply, String mode, List<?> stages, String reasoning) {}
}