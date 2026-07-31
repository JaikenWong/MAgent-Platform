package com.magent.platform.service.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Artifact;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.RpcError;
import com.magent.platform.dto.a2a.SendMessageParams;
import com.magent.platform.dto.a2a.TaskIdParams;
import com.magent.platform.dto.a2a.TaskListParams;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.entity.Agent;
import com.magent.platform.service.dify.DifyBlockingResult;
import com.magent.platform.service.dify.DifyClient;
import com.magent.platform.service.dify.DifyRequest;
import com.magent.platform.service.dify.DifyStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把一个 A2A JSON-RPC 调用桥接到 Dify.
 *
 *  blocking path 走 DifyClient.blocking + 一次 Task 生命周期闭环.
 *  streaming path by上层调用注入 DifyStreamHandler (见 A2AHostController).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2AServerService {

    private final DifyClient difyClient;
    private final TaskManagerService taskManager;
    private final ObjectMapper om;

    // ───── message/send (blocking) ─────

    public A2ATask handleSendBlocking(Agent agent, SendMessageParams params) {
        Message userMsg = params.message();
        if (userMsg == null) throw new BizException(RpcError.INVALID_PARAMS, "message required");
        String query = A2AMappers.firstText(userMsg);
        if (query.isBlank()) throw new BizException(RpcError.INVALID_PARAMS, "blank query");

        String contextId = userMsg.contextId() == null ? UUID.randomUUID().toString() : userMsg.contextId();
        A2ATask task = taskManager.create(contextId, agent.getId(), userMsg);
        taskManager.working(task.id());

        DifyRequest req = new DifyRequest(
            null,
            isChat(agent) ? query : null,
            task.id(),                                 // user = taskId (Dify end-user scoping)
            isChat(agent) ? "" : null,                 // chat 走空白 conversation (Phase 1 简化)
            null,
            "blocking"
        );
        if (!isChat(agent)) {
            // workflow app: query 进 inputs.query
            req = new DifyRequest(
                Map.of("query", query).toString(),
                null,
                task.id(),
                null,
                null,
                "blocking"
            );
        }

        try {
            DifyBlockingResult res = difyClient.blocking(baseUrl(agent), decryptKey(agent), req);
            Message agentMsg = new Message("agent",
                List.of(new TextPart(res.answer() == null ? extractTextFromOutputs(res.outputs()) : res.answer())));
            List<Artifact> arts = res.outputs() == null ? List.of() :
                List.of(new Artifact(UUID.randomUUID().toString(),
                    List.of(new TextPart(om.writeValueAsString(res.outputs())))));
            taskManager.appendMessage(task.id(), agentMsg);
            String error = res.error();
            if (error != null && !error.isBlank()) {
                log.warn("dify blocking returned error: {}", error);
                return taskManager.finish(task.id(), TaskState.FAILED, error, arts);
            }
            return taskManager.finish(task.id(), TaskState.COMPLETED, null, arts);
        } catch (BizException e) {
            taskManager.finish(task.id(), TaskState.FAILED, e.getMessage(), null);
            throw e;
        } catch (Exception e) {
            taskManager.finish(task.id(), TaskState.FAILED, e.getMessage(), null);
            throw new BizException(RpcError.INTERNAL_ERROR, "dify call failed: " + e.getMessage());
        }
    }

    // ───── message/stream ─────

    public void handleStream(Agent agent, SendMessageParams params, DifyStreamHandler handler) {
        Message userMsg = params.message();
        if (userMsg == null) throw new BizException(RpcError.INVALID_PARAMS, "message required");
        String query = A2AMappers.firstText(userMsg);
        String contextId = userMsg.contextId() == null ? UUID.randomUUID().toString() : userMsg.contextId();
        A2ATask task = taskManager.create(contextId, agent.getId(), userMsg);
        taskManager.working(task.id());

        DifyRequest req = isChat(agent)
            ? new DifyRequest(null, query, task.id(), "", null, "streaming")
            : new DifyRequest(Map.of("query", query).toString(), null, task.id(), null, null, "streaming");

        StringBuilder buf = new StringBuilder();
        DifyStreamHandler wrapped = new DifyStreamHandler() {
            @Override public void onText(String text) { buf.append(text); handler.onText(text); }
            @Override public void onReasoning(String r, boolean fin) { handler.onReasoning(r, fin); }
            @Override public void onNodeFinished(String n, String s, Map<String,Object> out) { handler.onNodeFinished(n, s, out); }
            @Override public void onHumanInputRequired(String a, String b, String c, String d) {
                handler.onHumanInputRequired(a, b, c, d);
            }
            @Override public void onError(String code, String msg) { handler.onError(code, msg); }
        };

        try {
            difyClient.stream(baseUrl(agent), decryptKey(agent), req, wrapped);
            Message agentMsg = new Message("agent", List.of(new TextPart(buf.toString())));
            taskManager.appendMessage(task.id(), agentMsg);
            List<Artifact> arts = List.of(new Artifact(UUID.randomUUID().toString(), agentMsg.parts()));
            taskManager.finish(task.id(), TaskState.COMPLETED, null, arts);
        } catch (RuntimeException e) {
            taskManager.finish(task.id(), TaskState.FAILED, e.getMessage(), null);
            if (e instanceof BizException be) throw be;
            throw new BizException(RpcError.INTERNAL_ERROR, "dify stream failed: " + e.getMessage());
        }
    }

    // ───── tasks/get | list | cancel ─────

    public A2ATask handleGetTask(Object params, Integer historyLength) {
        if (params instanceof TaskIdParams p) return taskManager.get(p.id(), historyLength);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) params;
        String id = (String) m.get("id");
        Integer hl = m.get("historyLength") instanceof Number n ? n.intValue() : historyLength;
        return taskManager.get(id, hl);
    }

    public List<A2ATask> handleListTasks(Object params) {
        if (params instanceof TaskListParams p) return taskManager.list(p);
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String,Object> m = (Map<String,Object>) params;
            return taskManager.list(new TaskListParams(
                (String) m.get("contextId"),
                (String) m.get("state"),
                m.get("limit") instanceof Number n ? n.intValue() : null));
        }
        return taskManager.list(null);
    }

    public A2ATask handleCancel(Object params) {
        if (params instanceof TaskIdParams p) return taskManager.cancel(p);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) params;
        return taskManager.cancel(new TaskIdParams((String) m.get("id"), null));
    }

    // ───── helpers ─────

    private boolean isChat(Agent a) {
        // Phase 1: 简单判定 — 有 query 就是 chat; workflow inputs 不带 query 字段.
        // 后续 Agent 表加 `dify_app_type` 字段精确区分.
        return a.getDifyAppId() != null && a.getDifyAppId().startsWith("chat-");
    }

    private String baseUrl(Agent a) {
        return a.getDifyBaseUrl() == null || a.getDifyBaseUrl().isBlank()
            ? difyClient.defaultBase() : a.getDifyBaseUrl();
    }

    private String decryptKey(Agent a) {
        String k = a.getDifyApiKey();
        if (k == null || k.isBlank()) throw new BizException(RpcError.INVALID_PARAMS,
            "dify_api_key not configured for agent " + a.getId());
        try { return CryptoUtil.decrypt(k); } catch (Exception e) {
            // 兼容旧数据: 未加密明文直接用
            log.warn("apikey not encrypted, using plaintext: {}", e.getMessage());
            return k;
        }
    }

    private String extractTextFromOutputs(Map<String,Object> outputs) {
        if (outputs == null) return "";
        Object s = outputs.get("text");
        if (s == null) s = outputs.get("answer");
        if (s == null) s = outputs.get("output");
        return s == null ? outputs.toString() : s.toString();
    }
}