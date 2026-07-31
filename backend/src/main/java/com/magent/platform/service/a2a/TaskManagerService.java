package com.magent.platform.service.a2a;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Artifact;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.RpcError;
import com.magent.platform.dto.a2a.TaskIdParams;
import com.magent.platform.dto.a2a.TaskListParams;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TaskStatus;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.magent.platform.entity.Task;
import com.magent.platform.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A2A Task 状态机 + DB 持久化.
 *  状态流转: submitted → working → input_required / completed / failed / canceled
 *  Task 的 messageHistory/artifacts 在 DB 中存 JSON 字符串.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagerService {

    private final TaskMapper taskMapper;
    private final ObjectMapper om;

    public A2ATask create(String contextId, String agentId, Message userMsg) {
        Task t = new Task();
        t.setId(UUID.randomUUID().toString());
        t.setContextId(contextId);
        t.setAssignedAgentId(agentId);
        t.setStatus(TaskState.SUBMITTED.name().toLowerCase());
        t.setCompletedAt(null);
        List<Message> hist = new ArrayList<>();
        if (userMsg != null) hist.add(userMsg);
        t.setMessageHistory(toJson(hist));
        t.setArtifacts(toJson(List.of()));
        taskMapper.insert(t);
        log.info("task created id={} agent={} ctx={}", t.getId(), agentId, contextId);
        return toDto(t);
    }

    public A2ATask working(String id) {
        Task t = require(id);
        if (!isTransitionAllowed(t.getStatus(), TaskState.WORKING)) {
            throw new BizException(RpcError.UNSUPPORTED_OP, "cannot transition to working from " + t.getStatus());
        }
        t.setStatus(TaskState.WORKING.name().toLowerCase());
        taskMapper.updateById(t);
        return toDto(t);
    }

    public A2ATask finish(String id, TaskState terminal, String error, List<Artifact> artifacts) {
        Task t = require(id);
        TaskState target = switch (terminal) {
            case COMPLETED, FAILED, CANCELED -> terminal;
            default -> throw new BizException("only terminal states");
        };
        if (!isTransitionAllowed(t.getStatus(), target)) {
            throw new BizException(RpcError.UNSUPPORTED_OP, "cannot transition to " + target + " from " + t.getStatus());
        }
        t.setStatus(target.name().toLowerCase());
        if (error != null) t.setErrorDetail(error);
        if (artifacts != null) t.setArtifacts(toJson(artifacts));
        t.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        return toDto(t);
    }

    public A2ATask inputRequired(String id, Message ask) {
        Task t = require(id);
        if (!isTransitionAllowed(t.getStatus(), TaskState.INPUT_REQUIRED)) {
            throw new BizException(RpcError.UNSUPPORTED_OP, "cannot transition to input_required from " + t.getStatus());
        }
        t.setStatus(TaskState.INPUT_REQUIRED.name().toLowerCase());
        appendMessage(t, ask);
        taskMapper.updateById(t);
        return toDto(t);
    }

    public A2ATask appendMessage(String id, Message msg) {
        Task t = require(id);
        appendMessage(t, msg);
        taskMapper.updateById(t);
        return toDto(t);
    }

    public A2ATask get(String id, Integer historyLength) {
        Task t = require(id);
        return toDto(t, historyLength);
    }

    public List<A2ATask> list(TaskListParams params) {
        QueryWrapper<Task> w = new QueryWrapper<Task>().orderByDesc("created_at");
        if (params != null) {
            if (params.contextId() != null && !params.contextId().isBlank()) w.eq("context_id", params.contextId());
            if (params.state() != null && !params.state().isBlank()) w.eq("status", params.state().toLowerCase());
            int lim = params.limit() == null || params.limit() < 1 ? 50 : Math.min(params.limit(), 500);
            w.last("limit " + lim);
        }
        List<Task> rows = taskMapper.selectList(w);
        return rows.stream().map(t -> toDto(t, 0)).toList();
    }

    public A2ATask cancel(TaskIdParams params) {
        Task t = require(params.id());
        String s = t.getStatus();
        if (TaskState.COMPLETED.name().equalsIgnoreCase(s)
                || TaskState.FAILED.name().equalsIgnoreCase(s)
                || TaskState.CANCELED.name().equalsIgnoreCase(s)) {
            throw new BizException(RpcError.TASK_NOT_CANCELABLE, "task in terminal state: " + s);
        }
        t.setStatus(TaskState.CANCELED.name().toLowerCase());
        t.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        return toDto(t);
    }

    public A2ATask appendArtifact(String id, Artifact artifact) {
        Task t = require(id);
        List<Artifact> arts = parseArtifacts(t.getArtifacts());
        arts.add(artifact);
        t.setArtifacts(toJson(arts));
        taskMapper.updateById(t);
        return toDto(t);
    }

    // ───────── helpers ─────────

    private Task require(String id) {
        if (id == null || id.isBlank()) throw new BizException(RpcError.INVALID_PARAMS, "task id required");
        Task t = taskMapper.selectById(id);
        if (t == null) throw new BizException(RpcError.TASK_NOT_FOUND, "task not found: " + id);
        return t;
    }

    private void appendMessage(Task t, Message msg) {
        List<Message> hist = parseHistory(t.getMessageHistory());
        hist.add(msg);
        t.setMessageHistory(toJson(hist));
    }

    private boolean isTransitionAllowed(String current, TaskState target) {
        String cur = current == null ? "submitted" : current.toLowerCase();
        return switch (target) {
            case WORKING -> "submitted".equals(cur) || "working".equals(cur) || "input_required".equals(cur);
            case INPUT_REQUIRED -> "working".equals(cur);
            case COMPLETED, FAILED, CANCELED -> "working".equals(cur) || "input_required".equals(cur) || "submitted".equals(cur);
            default -> false;
        };
    }

    private List<Message> parseHistory(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return om.readValue(json, new TypeReference<List<Message>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private List<Artifact> parseArtifacts(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return om.readValue(json, new TypeReference<List<Artifact>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "[]"; }
    }

    private A2ATask toDto(Task t) { return toDto(t, null); }

    private A2ATask toDto(Task t, Integer historyLength) {
        String ts = t.getUpdatedAt() == null ? t.getCreatedAt().toString() : t.getUpdatedAt().toString();
        TaskStatus status = new TaskStatus(
            TaskState.valueOf(t.getStatus().toUpperCase()),
            ts, null);
        List<Artifact> arts = parseArtifacts(t.getArtifacts());
        List<Message> hist;
        if (historyLength != null && historyLength >= 0) {
            List<Message> all = parseHistory(t.getMessageHistory());
            int from = Math.max(0, all.size() - historyLength);
            hist = historyLength == 0 ? null : all.subList(from, all.size());
        } else {
            hist = parseHistory(t.getMessageHistory());
        }
        return new A2ATask(t.getId(), t.getContextId(), status, arts, hist, "task");
    }
}