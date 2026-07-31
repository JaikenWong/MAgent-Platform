package com.magent.platform.service.orchestrator;

import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.dto.orchestrator.Stage;
import com.magent.platform.entity.Agent;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.service.a2a.A2AClientService;
import com.magent.platform.service.a2a.A2AMappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Executor: 按 ExecutionPlan 执行 Agent 链.
 */
@Slf4j
@Service
public class ExecutorService {

    private final A2AClientService a2aClient;
    private final AgentMapper agentMapper;
    private final Executor taskExecutor;

    public ExecutorService(A2AClientService a2aClient, AgentMapper agentMapper,
                           @Qualifier("agentExecutor") Executor taskExecutor) {
        this.a2aClient = a2aClient;
        this.agentMapper = agentMapper;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 执行编排计划, 返回各 Agent 的输出.
     * @param plan 执行计划
     * @param contextId A2A context (跨 task 共享)
     * @return ordered results [{agentName, output}]
     */
    public List<Map<String, String>> execute(ExecutionPlan plan, String contextId) {
        return switch (plan.executionMode()) {
            case "parallel" -> executeParallel(plan, contextId);
            case "router" -> executeRouter(plan, contextId);
            default -> executeSequential(plan, contextId);
        };
    }

    private List<Map<String, String>> executeSequential(ExecutionPlan plan, String contextId) {
        List<Map<String, String>> results = new ArrayList<>();
        String previousOutput = null;

        for (Stage stage : plan.stages()) {
            Agent agent = loadAgent(stage.agentId());
            if (agent == null) {
                log.warn("agent not found: {}", stage.agentId());
                results.add(resultMap(stage, "[agent not found]"));
                continue;
            }

            String input = "previous".equals(stage.inputFrom()) && previousOutput != null ? previousOutput : stage.description();
            log.info("sequential: calling agent {} ({})", agent.getName(), stage.agentId());

            try {
                Message msg = new Message("user", List.of(new TextPart(input)), contextId, null, null, null);
                A2ATask task = a2aClient.sendMessage(agent, msg, true);
                String output = extractTaskOutput(task);
                previousOutput = output;
                results.add(resultMap(stage, output));
            } catch (Exception e) {
                log.error("agent {} failed", agent.getName(), e);
                results.add(resultMap(stage, "[error: " + e.getMessage() + "]"));
            }
        }
        return results;
    }

    private List<Map<String, String>> executeParallel(ExecutionPlan plan, String contextId) {
        List<CompletableFuture<Map.Entry<Integer, Map<String, String>>>> futures = new ArrayList<>();

        for (int i = 0; i < plan.stages().size(); i++) {
            final Stage stage = plan.stages().get(i);
            final int idx = i;
            var future = CompletableFuture.supplyAsync(() -> {
                Agent agent = loadAgent(stage.agentId());
                if (agent == null) {
                    return Map.entry(idx, resultMap(stage, "[agent not found]"));
                }
                try {
                    String input = stage.description();
                    Message msg = new Message("user", List.of(new TextPart(input)), contextId, null, null, null);
                    A2ATask task = a2aClient.sendMessage(agent, msg, true);
                    String output = extractTaskOutput(task);
                    return Map.entry(idx, resultMap(stage, output));
                } catch (Exception e) {
                    log.error("parallel agent {} failed", agent.getName(), e);
                    return Map.entry(idx, resultMap(stage, "[error: " + e.getMessage() + "]"));
                }
            }, taskExecutor);
            futures.add(future);
        }

        // wait all and sort by original order
        return futures.stream()
            .map(CompletableFuture::join)
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(java.util.stream.Collectors.toList());
    }

    private List<Map<String, String>> executeRouter(ExecutionPlan plan, String contextId) {
        // Router: use the first stage's agent
        if (plan.stages().isEmpty()) return List.of();
        Stage stage = plan.stages().get(0);
        Agent agent = loadAgent(stage.agentId());
        if (agent == null) {
            return List.of(resultMap(stage, "[agent not found]"));
        }
        try {
            Message msg = new Message("user", List.of(new TextPart(stage.description())), contextId, null, null, null);
            A2ATask task = a2aClient.sendMessage(agent, msg, true);
            return List.of(resultMap(stage, extractTaskOutput(task)));
        } catch (Exception e) {
            log.error("router agent {} failed", agent.getName(), e);
            return List.of(resultMap(stage, "[error: " + e.getMessage() + "]"));
        }
    }

    private Agent loadAgent(String agentId) {
        return agentMapper.selectById(agentId);
    }

    private String extractTaskOutput(A2ATask task) {
        if (task == null) return "[no response]";
        if (task.status() != null && task.status().message() != null) {
            return A2AMappers.firstText(task.status().message());
        }
        if (task.artifacts() != null && !task.artifacts().isEmpty()) {
            return A2AMappers.firstText(
                new Message("agent", task.artifacts().get(0).parts(), null, null, null, null));
        }
        return "[empty response]";
    }

    private Map<String, String> resultMap(Stage stage, String output) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("agentId", stage.agentId());
        m.put("agentName", stage.agentName());
        m.put("output", output);
        return m;
    }
}