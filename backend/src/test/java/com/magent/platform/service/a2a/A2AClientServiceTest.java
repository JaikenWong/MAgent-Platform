package com.magent.platform.service.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.entity.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class A2AClientServiceTest {

    private A2AClientService client;
    private MockRestServiceServer server;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new A2AClientService(om, builder.build(), "http://test-host:8080");
    }

    @Test
    void sendMessage_buildsCorrectUrlAndParsesResponse() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-123");
        agent.setName("test-agent");

        String taskJson = """
            {"jsonrpc":"2.0","id":1,"result":{
              "id":"task-abc",
              "contextId":"ctx-1",
              "status":{"state":"COMPLETED","timestamp":"2026-07-31T12:00:00Z"},
              "artifacts":[],
              "history":[],
              "kind":"task"
            }}
            """;

        server.expect(requestTo("http://test-host:8080/a2a/agent-123"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(jsonPath("$.method").value("message/send"))
            .andRespond(withSuccess(taskJson, MediaType.APPLICATION_JSON));

        Message msg = new Message("user", List.of(new TextPart("hello")), null, null, null, null);
        A2ATask task = client.sendMessage(agent, msg, true);

        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("task-abc");
        assertThat(task.contextId()).isEqualTo("ctx-1");
        server.verify();
    }

    @Test
    void rpcError_throwsBizException() {
        Agent agent = new Agent();
        agent.setId("agent-err");
        agent.setName("err-agent");

        String errorJson = """
            {"jsonrpc":"2.0","id":1,"error":{"code":-32001,"message":"task not found"}}
            """;

        server.expect(requestTo("http://test-host:8080/a2a/agent-err"))
            .andRespond(withSuccess(errorJson, MediaType.APPLICATION_JSON));

        Message msg = new Message("user", List.of(new TextPart("hi")), null, null, null, null);

        assertThatThrownBy(() -> client.sendMessage(agent, msg, true))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("task not found");
    }

    @Test
    void getTask_buildsCorrectMethod() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-456");

        String taskJson = """
            {"jsonrpc":"2.0","id":1,"result":{
              "id":"task-x","contextId":"ctx","status":{"state":"WORKING"},"artifacts":[],"history":[],"kind":"task"
            }}
            """;

        server.expect(requestTo("http://test-host:8080/a2a/agent-456"))
            .andExpect(jsonPath("$.method").value("tasks/get"))
            .andRespond(withSuccess(taskJson, MediaType.APPLICATION_JSON));

        A2ATask task = client.getTask(agent, "task-x");
        assertThat(task.id()).isEqualTo("task-x");
    }

    @Test
    void cancelTask_buildsCorrectMethod() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-789");

        String taskJson = """
            {"jsonrpc":"2.0","id":1,"result":{
              "id":"task-c","contextId":"ctx","status":{"state":"CANCELED"},"artifacts":[],"history":[],"kind":"task"
            }}
            """;

        server.expect(requestTo("http://test-host:8080/a2a/agent-789"))
            .andExpect(jsonPath("$.method").value("tasks/cancel"))
            .andRespond(withSuccess(taskJson, MediaType.APPLICATION_JSON));

        A2ATask task = client.cancelTask(agent, "task-c");
        assertThat(task.id()).isEqualTo("task-c");
    }
}