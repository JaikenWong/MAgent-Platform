package com.magent.platform.service.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.RpcError;
import com.magent.platform.dto.a2a.RpcRequest;
import com.magent.platform.dto.a2a.RpcResponse;
import com.magent.platform.dto.a2a.SendMessageParams;
import com.magent.platform.dto.a2a.TaskIdParams;
import com.magent.platform.dto.a2a.TaskListParams;
import com.magent.platform.entity.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * A2A Client: 以 JSON-RPC 方式调其他 Agent 的 A2A 端点.
 *  非本轮服务的其他 agent, 而是 orchestrator 作为 client 去调已注册的 A2A server agents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2AClientService {

    private final ObjectMapper om;
    private final RestClient restClient = RestClient.create();

    @Value("${magent.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public A2ATask sendMessage(Agent agent, Message message, boolean blocking) {
        SendMessageParams params = new SendMessageParams(message, null);
        RpcRequest req = RpcRequest.create(blocking ? "message/send" : "message/stream", params);
        RpcResponse resp = call(agent, req);
        if (resp.error() != null) {
            throw new BizException(resp.error().code(), resp.error().message());
        }
        try {
            return om.convertValue(resp.result(), A2ATask.class);
        } catch (Exception e) {
            throw new BizException(RpcError.INTERNAL_ERROR, "failed to parse task response: " + e.getMessage());
        }
    }

    public A2ATask getTask(Agent agent, String taskId) {
        RpcRequest req = RpcRequest.create("tasks/get", new TaskIdParams(taskId, null));
        RpcResponse resp = call(agent, req);
        if (resp.error() != null) {
            throw new BizException(resp.error().code(), resp.error().message());
        }
        try {
            return om.convertValue(resp.result(), A2ATask.class);
        } catch (Exception e) {
            throw new BizException(RpcError.INTERNAL_ERROR, "failed to parse task: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<A2ATask> listTasks(Agent agent, TaskListParams params) {
        RpcRequest req = RpcRequest.create("tasks/list", params);
        RpcResponse resp = call(agent, req);
        if (resp.error() != null) {
            throw new BizException(resp.error().code(), resp.error().message());
        }
        try {
            return om.convertValue(resp.result(), om.getTypeFactory().constructCollectionType(List.class, A2ATask.class));
        } catch (Exception e) {
            throw new BizException(RpcError.INTERNAL_ERROR, "failed to parse task list: " + e.getMessage());
        }
    }

    public A2ATask cancelTask(Agent agent, String taskId) {
        RpcRequest req = RpcRequest.create("tasks/cancel", new TaskIdParams(taskId, null));
        RpcResponse resp = call(agent, req);
        if (resp.error() != null) {
            throw new BizException(resp.error().code(), resp.error().message());
        }
        try {
            return om.convertValue(resp.result(), A2ATask.class);
        } catch (Exception e) {
            throw new BizException(RpcError.INTERNAL_ERROR, "failed to parse canceled task: " + e.getMessage());
        }
    }

    String agentUrl(Agent agent) {
        return publicBaseUrl + "/a2a/" + agent.getId();
    }

    private RpcResponse call(Agent agent, RpcRequest req) {
        String url = agentUrl(agent);
        log.debug("a2a call {} {}", req.method(), url);
        try {
            String json = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(om.writeValueAsString(req))
                .retrieve()
                .body(String.class);
            return om.readValue(json, RpcResponse.class);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(RpcError.INTERNAL_ERROR,
                "a2a call to " + agent.getName() + " failed: " + e.getMessage());
        }
    }
}