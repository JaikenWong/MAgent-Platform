package com.magent.platform.service.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.a2a.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class A2AProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试 A2A JSON-RPC 2.0 请求与响应序列化/反序列化")
    void testA2AJsonRpcFlow() throws Exception {
        // 1. 模拟构建发送消息的 A2A JSON-RPC 请求 (SendMessageParams)
        Part textPart = new TextPart("请帮我分析这份数据");
        Message message = new Message("user", List.of(textPart));
        SendMessageParams params = new SendMessageParams(message, null);

        RpcRequest request = RpcRequest.create("message/send", params);

        // 序列化为 JSON 字符串 (模拟网络传输)
        String requestJson = objectMapper.writeValueAsString(request);
        assertNotNull(requestJson);
        assertTrue(requestJson.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(requestJson.contains("\"method\":\"message/send\""));

        // 2. 服务端反序列化 RPC 请求
        RpcRequest readRequest = objectMapper.readValue(requestJson, RpcRequest.class);
        assertEquals("2.0", readRequest.jsonrpc());
        assertEquals("message/send", readRequest.method());

        SendMessageParams readParams = readRequest.paramsAs(SendMessageParams.class, objectMapper);
        assertEquals("user", readParams.message().role());

        // 3. 服务端处理并返回 A2A Task 响应 (Task 状态为 WORKING)
        TaskStatus taskStatus = new TaskStatus(TaskState.WORKING, "2026-08-01T10:00:00Z", message);
        A2ATask task = new A2ATask("task-999", "context-888", taskStatus, null, null, null);

        RpcResponse response = RpcResponse.ok(readRequest.id(), task);

        String responseJson = objectMapper.writeValueAsString(response);
        assertTrue(responseJson.contains("\"state\":\"working\""));
        assertTrue(responseJson.contains("\"id\":\"task-999\""));

        // 4. 客户端解析 Task 响应
        RpcResponse readResponse = objectMapper.readValue(responseJson, RpcResponse.class);
        assertEquals(readRequest.id(), readResponse.id());
        assertNull(readResponse.error());
        assertNotNull(readResponse.result());
    }
}

