package com.magent.platform.controller.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.R;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.AgentCard;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.RpcError;
import com.magent.platform.dto.a2a.RpcRequest;
import com.magent.platform.dto.a2a.RpcResponse;
import com.magent.platform.dto.a2a.SendMessageParams;
import com.magent.platform.dto.a2a.TaskIdParams;
import com.magent.platform.dto.a2a.TaskListParams;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TaskStatus;
import com.magent.platform.entity.Agent;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.service.a2a.A2AServerService;
import com.magent.platform.service.a2a.AgentCardService;
import com.magent.platform.service.dify.DifyStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A2A Protocol Host — 每个注册的 Agent 公开标准化 A2A endpoints.
 *   GET  /a2a/{agentId}/.well-known/agent-card.json  : Agent Card 发现 (公开)
 *   POST /a2a/{agentId}                              : JSON-RPC (message/send, tasks/get|list|cancel)
 *   POST /a2a/{agentId}/stream                       : JSON-RPC streaming 走 SSE (message/stream)
 */
@Slf4j
@RestController
@RequestMapping("/a2a")
@RequiredArgsConstructor
public class A2AHostController {

    private final AgentMapper agentMapper;
    private final AgentCardService cardService;
    private final A2AServerService server;
    private final ObjectMapper om;

    @Value("${magent.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    // ───── Agent Card ─────

    @GetMapping("/{agentId}/.well-known/agent-card.json")
    public AgentCard agentCard(@PathVariable String agentId) {
        Agent a = loadAgent(agentId);
        return cardService.build(a, publicBaseUrl);
    }

    // ───── JSON-RPC: /{agentId} (blocking) ─────

    @PostMapping(value = "/{agentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RpcResponse rpc(@PathVariable String agentId, @RequestBody RpcRequest req) {
        Agent a = loadAgent(agentId);
        String method = req.method();
        try {
            return switch (method) {
                case "message/send" -> {
                    SendMessageParams p = req.paramsAs(SendMessageParams.class, om);
                    A2ATask task = server.handleSendBlocking(a, p);
                    yield RpcResponse.ok(req.id(), task);
                }
                case "tasks/get" -> {
                    Object r = server.handleGetTask(om.convertValue(req.params(), Map.class), null);
                    yield RpcResponse.ok(req.id(), r);
                }
                case "tasks/list" -> {
                    Object r = server.handleListTasks(om.convertValue(req.params(), Map.class));
                    yield RpcResponse.ok(req.id(), r);
                }
                case "tasks/cancel" -> {
                    A2ATask task = server.handleCancel(om.convertValue(req.params(), Map.class));
                    yield RpcResponse.ok(req.id(), task);
                }
                default -> RpcResponse.err(req.id(),
                        new RpcError(RpcError.METHOD_NOT_FOUND, "method not supported: " + method, null));
            };
        } catch (BizException e) {
            int code = e.getCode() >= 400 && e.getCode() <= 599 ? RpcError.INTERNAL_ERROR : e.getCode();
            return RpcResponse.err(req.id(), new RpcError(code, e.getMessage(), null));
        } catch (Exception e) {
            log.error("a2a rpc failed agent={} method={} : {}", agentId, method, e.getMessage(), e);
            return RpcResponse.err(req.id(), new RpcError(RpcError.INTERNAL_ERROR, e.getMessage(), null));
        }
    }

    // ───── JSON-RPC: /{agentId}/stream (SSE) ─────

    @PostMapping(value = "/{agentId}/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String agentId, @RequestBody RpcRequest req) {
        Agent a = loadAgent(agentId);
        // SSE 心跳 timeout 10 min (Dify 长任务兜底)
        SseEmitter emitter = new SseEmitter(10L * 60_000);

        SendMessageParams p;
        try {
            p = req.paramsAs(SendMessageParams.class, om);
        } catch (Exception e) {
            sendErrorEvent(emitter, req.id(), RpcError.INVALID_PARAMS, "bad params: " + e.getMessage());
            return emitter;
        }

        DifyStreamHandler handler = new DifyStreamHandler() {
            private volatile String taskId;
            @Override public void onText(String text) {
                sendEvent(emitter, Map.of("kind", "ArtifactUpdate",
                    "artifactId", UUID.randomUUID().toString(),
                    "parts", List.of(Map.of("type", "text", "text", text))));
            }
            @Override public void onReasoning(String r, boolean fin) {
                sendEvent(emitter, Map.of("kind", "StatusUpdate",
                    "reasoning", r, "is_final", fin));
            }
            @Override public void onHumanInputRequired(String tok, String content, String node, String exp) {
                sendEvent(emitter, Map.of("kind", "StatusUpdate",
                    "state", "input_required", "form_token", tok, "content", content, "node_id", node));
            }
            @Override public void onError(String code, String msg) {
                sendErrorEvent(emitter, req.id(), RpcError.INTERNAL_ERROR, msg);
            }
        };

        new Thread(() -> {
            try {
                server.handleStream(a, p, handler);
                A2ATask finalTask = currentTaskForReq(p);
                emitter.send(SseEmitter.event().data(RpcResponse.ok(req.id(), finalTask)));
                emitter.complete();
            } catch (BizException be) {
                sendErrorEvent(emitter, req.id(),
                        be.getCode() == 0 ? RpcError.INTERNAL_ERROR : be.getCode(), be.getMessage());
            } catch (Exception ex) {
                log.error("a2a stream agent={} failed: {}", agentId, ex.getMessage(), ex);
                sendErrorEvent(emitter, req.id(), RpcError.INTERNAL_ERROR, ex.getMessage());
            }
        }).start();
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException e) {
            log.debug("sse send failed: {}", e.getMessage());
        }
    }

    private void sendErrorEvent(SseEmitter emitter, Object id, int code, String msg) {
        try {
            emitter.send(SseEmitter.event().data(
                RpcResponse.err(id, new RpcError(code, msg, null))));
        } catch (IOException ignored) {}
        try { emitter.completeWithError(new BizException(code, msg)); } catch (Exception ignored) {}
    }

    private A2ATask currentTaskForReq(SendMessageParams p) {
        // 实际最新 task 已在 handleStream 内 finish; 这里返回一个最小占位,
        // 上层要拿真实 Task 请调 message/send 阻塞版或 tasks/get.
        return new A2ATask(
            UUID.randomUUID().toString(),
            p.message() != null ? p.message().contextId() : null,
            new TaskStatus(TaskState.COMPLETED, java.time.OffsetDateTime.now().toString(), null),
            null, null, "task");
    }

    private Agent loadAgent(String id) {
        Agent a = agentMapper.selectOne(new QueryWrapper<Agent>().eq("id", id));
        if (a == null) throw new BizException(404, "agent not found: " + id);
        return a;
    }
}