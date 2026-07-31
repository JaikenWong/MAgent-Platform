package com.magent.platform.service.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify REST API 客户端.
 *  - POST {base}/chat-messages    (Chatflow / Agent)
 *  - POST {base}/workflows/run    (Workflow)
 *  - POST {base}/files/upload     (后续 Phase)
 *
 * 使用 JDK 自带 HttpClient, SSE 用 BufferedReader.readLine() 按 SSE 协议解析.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyClient {

    private final ObjectMapper objectMapper;

    @Value("${magent.dify.base-url:http://dify.local:3001/v1}")
    private String defaultBase;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // ───────── 阻塞模式 ─────────

    public DifyBlockingResult blocking(String baseUrl, String apiKey, DifyRequest req) {
        String url = baseUrl + (req.isChat() ? "/chat-messages" : "/workflows/run");
        try {
            HttpResponse<String> resp = http.send(
                reqFor(url, apiKey, req, "blocking"),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = resp.statusCode();
            if (sc >= 400) {
                JsonNode err = objectMapper.readTree(resp.body());
                throw new BizException(sc, err.path("message").asText("dify error: " + resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            DifyBlockingResult result = parseBlocking(root);
            log.info("dify blocking {} ok answer_bytes={}",
                    req.isChat() ? "chat" : "workflow",
                    result.answer() == null ? 0 : result.answer().length());
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "dify call failed: " + e.getMessage());
        }
    }

    private DifyBlockingResult parseBlocking(JsonNode root) {
        if (root.has("answer")) {
            // chat blocking
            return new DifyBlockingResult(
                root.path("answer").asText(null),
                root.path("conversation_id").asText(null),
                root.path("message_id").asText(null),
                root.path("task_id").asText(null),
                null,
                null,
                null
            );
        } else {
            // workflow blocking: { task_id, workflow_run_id, data:{ outputs, status, error } }
            JsonNode data = root.path("data");
            return new DifyBlockingResult(
                null,
                null,
                null,
                root.path("task_id").asText(null),
                root.path("workflow_run_id").asText(null),
                data.path("outputs").isObject() ? objectMapper.convertValue(data.path("outputs"), Map.class) : null,
                data.path("error").asText(null)
            );
        }
    }

    // ───────── 流式模式 ─────────

    /**
     * 调 Dify 流式接口, 逐事件回调 `handler`. 阻塞直到流结束.
     * Agent app 必须 streaming (Dify 限制), Workflow 可选.
     */
    public void stream(String baseUrl, String apiKey, DifyRequest req, DifyStreamHandler handler) {
        String url = baseUrl + (req.isChat() ? "/chat-messages" : "/workflows/run");
        try {
            HttpResponse<java.io.InputStream> resp = http.send(
                reqFor(url, apiKey, req, "streaming"),
                HttpResponse.BodyHandlers.ofInputStream());
            int sc = resp.statusCode();
            if (sc >= 400) {
                String body = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode err = objectMapper.readTree(body);
                throw new BizException(sc, err.path("message").asText("dify error: " + body));
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String event = null;
                List<String> dataLines = new ArrayList<>(2);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (event != null && !dataLines.isEmpty()) {
                            dispatch(event, String.join("\n", dataLines), handler);
                        }
                        event = null;
                        dataLines.clear();
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        dataLines.add(line.substring(5).trim());
                    } else if (line.startsWith(":")) {
                        // SSE comment / keep-alive
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "dify stream failed: " + e.getMessage());
        }
    }

    private void dispatch(String event, String data, DifyStreamHandler handler) {
        if ("ping".equalsIgnoreCase(event)) return;
        try {
            JsonNode node = objectMapper.readTree(data);
            switch (event) {
                case "message", "agent_message" -> handler.onText(node.path("answer").asText(""));
                case "text_chunk" -> handler.onText(node.path("data").path("text").asText(""));
                case "agent_thought" -> handler.onAgentThought(
                        node.path("thought").asText(null),
                        node.path("tool").asText(null),
                        node.path("tool_input").asText(null),
                        node.path("observation").asText(null));
                case "reasoning_chunk" -> {
                    JsonNode dataNode = node.path("data");
                    boolean isFinal = dataNode.path("is_final").asBoolean(false);
                    String reasoning = dataNode.path("reasoning").asText("");
                    handler.onReasoning(reasoning, isFinal);
                }
                case "node_finished" -> {
                    JsonNode d = node.path("data");
                    handler.onNodeFinished(
                        d.path("node_id").asText(null),
                        d.path("status").asText(null),
                        d.path("outputs").isObject()
                            ? objectMapper.convertValue(d.path("outputs"), Map.class)
                            : Map.of());
                }
                case "workflow_finished" -> {
                    JsonNode d = node.path("data");
                    handler.onWorkflowFinished(
                        d.path("status").asText("succeeded"),
                        d.path("outputs").isObject()
                            ? objectMapper.convertValue(d.path("outputs"), Map.class)
                            : Map.of(),
                        d.path("error").asText(null));
                }
                case "message_end" -> handler.onMessageEnd(
                    node.path("metadata").isObject()
                        ? objectMapper.convertValue(node.path("metadata"), Map.class)
                        : Map.of());
                case "workflow_paused", "human_input_required" -> {
                    JsonNode d = node.path("data").isObject() ? node.path("data") : node;
                    handler.onHumanInputRequired(
                            d.path("form_token").asText(null),
                            d.path("form_content").asText(null),
                            d.path("node_id").asText(null),
                            d.path("expiration_time").asText(null));
                }
                case "error" -> handler.onError(
                        node.path("code").asText("error"),
                        node.path("message").asText("dify error in stream"));
                default -> log.debug("dify event ignored: {} data={}", event, data);
            }
        } catch (Exception e) {
            log.warn("dify dispatch failed for event={} data={}: {}", event, data, e.getMessage());
        }
    }

    // ───────── helpers ─────────

    private HttpRequest reqFor(String url, String apiKey, DifyRequest req, String responseMode)
            throws IOException {
        Map<String, Object> body = new HashMap<>();
        if (req.inputs() != null) {
            Map<String, Object> inputs = objectMapper.readValue(req.inputs(), Map.class);
            body.put("inputs", inputs);
        } else {
            body.put("inputs", Map.of());
        }
        if (req.isChat()) {
            body.put("query", req.query());
            body.put("conversation_id", req.conversationId() == null ? "" : req.conversationId());
        }
        body.put("user", req.user());
        if (req.files() != null) body.put("files", req.files());
        body.put("response_mode", responseMode);

        byte[] payload = objectMapper.writeValueAsBytes(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
    }

    public String defaultBase() { return defaultBase; }
}