package com.magent.platform.service.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TaskStatus;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.dto.orchestrator.Stage;
import com.magent.platform.entity.Agent;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.service.a2a.A2AClientService;
import com.magent.platform.service.a2a.TaskManagerService;
import com.magent.platform.service.approval.ApprovalEngine;
import com.magent.platform.service.approval.ApprovalNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorServiceTest {

    @Mock private A2AClientService a2aClient;
    @Mock private AgentMapper agentMapper;
    @Mock private ApprovalEngine approvalEngine;
    @Mock private ApprovalNotifier approvalNotifier;
    @Mock private TaskManagerService taskManagerService;

    private ExecutorService executorService;
    private final ObjectMapper om = new ObjectMapper();

    private Agent agent1;
    private Agent agent2;

    @BeforeEach
    void setup() {
        executorService = new ExecutorService(a2aClient, agentMapper, Executors.newFixedThreadPool(4),
            approvalEngine, approvalNotifier, taskManagerService, om);

        agent1 = new Agent();
        agent1.setId("agent-001");
        agent1.setName("研究员");

        agent2 = new Agent();
        agent2.setId("agent-002");
        agent2.setName("撰稿人");
    }

    @Test
    void sequential_passesOutputToNextStage() {
        Stage s1 = new Stage("agent-001", "研究员", "user", "research", 0);
        Stage s2 = new Stage("agent-002", "撰稿人", "previous", "write", 1);
        ExecutionPlan plan = ExecutionPlan.of("sequential", List.of(s1, s2), "test");

        when(agentMapper.selectById("agent-001")).thenReturn(agent1);
        when(agentMapper.selectById("agent-002")).thenReturn(agent2);

        A2ATask task1 = taskWithMessage("研究结论：竞品定价 99 元");
        A2ATask task2 = taskWithMessage("建议书草稿已生成");

        when(a2aClient.sendMessage(any(Agent.class), any(Message.class), anyBoolean()))
            .thenReturn(task1)
            .thenReturn(task2);

        List<Map<String, String>> results = executorService.execute(plan, "ctx-1");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("output")).contains("研究结论");
        assertThat(results.get(1).get("output")).contains("建议书草稿");
    }

    @Test
    void router_executesOnlyFirstStage() {
        Stage s1 = new Stage("agent-001", "研究员", "user", "answer", 0);
        ExecutionPlan plan = ExecutionPlan.of("router", List.of(s1), "test");

        when(agentMapper.selectById("agent-001")).thenReturn(agent1);
        when(a2aClient.sendMessage(any(Agent.class), any(Message.class), anyBoolean()))
            .thenReturn(taskWithMessage("路由结果"));

        List<Map<String, String>> results = executorService.execute(plan, "ctx-2");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("output")).isEqualTo("路由结果");
    }

    @Test
    void agentNotFound_returnsErrorMessage() {
        Stage s1 = new Stage("missing", "missing", "user", "x", 0);
        ExecutionPlan plan = ExecutionPlan.of("sequential", List.of(s1), "test");

        when(agentMapper.selectById("missing")).thenReturn(null);

        List<Map<String, String>> results = executorService.execute(plan, "ctx-3");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("output")).contains("agent not found");
    }

    @Test
    void parallel_executesAllStages() {
        Stage s1 = new Stage("agent-001", "研究员", "user", "research", 0);
        Stage s2 = new Stage("agent-002", "撰稿人", "user", "write", 1);
        ExecutionPlan plan = ExecutionPlan.of("parallel", List.of(s1, s2), "test");

        when(agentMapper.selectById("agent-001")).thenReturn(agent1);
        when(agentMapper.selectById("agent-002")).thenReturn(agent2);
        when(a2aClient.sendMessage(any(Agent.class), any(Message.class), anyBoolean()))
            .thenReturn(taskWithMessage("result-1"))
            .thenReturn(taskWithMessage("result-2"));

        List<Map<String, String>> results = executorService.execute(plan, "ctx-4");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("agentId")).isEqualTo("agent-001");
        assertThat(results.get(1).get("agentId")).isEqualTo("agent-002");
    }

    private A2ATask taskWithMessage(String text) {
        Message msg = new Message("agent", List.of(new TextPart(text)), null, null, null, null);
        return new A2ATask(
            "task-id",
            "ctx",
            new TaskStatus(TaskState.COMPLETED, "2026-07-31T12:00:00Z", msg),
            List.of(),
            List.of(),
            "task"
        );
    }
}