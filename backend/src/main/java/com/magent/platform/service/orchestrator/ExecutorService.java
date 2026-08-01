package com.magent.platform.service.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.a2a.A2ATask;
import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.TaskState;
import com.magent.platform.dto.a2a.TextPart;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.dto.orchestrator.Stage;
import com.magent.platform.entity.Agent;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.service.a2a.A2AClientService;
import com.magent.platform.service.a2a.A2AMappers;
import com.magent.platform.service.a2a.TaskManagerService;
import com.magent.platform.service.approval.ApprovalEngine;
import com.magent.platform.service.approval.ApprovalNotifier;
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
import java.util.stream.Collectors;

/**
 * Executor: 按 ExecutionPlan 执行 Agent 链.
 * 支持顺序/并行/条件/路由四种模式, 调 Agent 前检查 approvalSkills.
 */
@Slf4j
@Service
public class ExecutorService {

    private final A2AClientService a2aClient;
    private final AgentMapper agentMapper;
    private final Executor taskExecutor;
    private final ApprovalEngine approvalEngine;
    private final ApprovalNotifier approvalNotifier;
    private final TaskManagerService taskManagerService;
    private final ObjectMapper om;

    public ExecutorService(A2AClientService a2aClient, AgentMapper agentMapper,
                           @Qualifier("agentExecutor") Executor taskExecutor,
                           ApprovalEngine approvalEngine,
                           ApprovalNotifier approvalNotifier,
                           TaskManagerService taskManagerService,
                           ObjectMapper om) {
        this.a2aClient = a2aClient;
        this.agentMapper = agentMapper;
        this.taskExecutor = taskExecutor;
        this.approvalEngine = approvalEngine;
        this.approvalNotifier = approvalNotifier;
        this.taskManagerService = taskManagerService;
        this.om = om;
    }

    public List<Map<String, String>> execute(ExecutionPlan plan, String contextId) {
        return switch (plan.executionMode()) {
            case "parallel" -> executeParallel(plan, contextId);
            case "router" -> executeRouter(plan, contextId);
            case "conditional" -> executeConditional(plan, contextId);
            default -> executeSequential(plan, contextId);
        };
    }

    // ───── sequential ─────

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

            // Approval check
            String approvalSkill = checkApprovalNeeded(agent, stage);
            if (approvalSkill != null) {
                String taskId = createApprovalTask(contextId, agent, input);
                boolean required = approvalEngine.requestApproval(taskId, agent.getId(), approvalSkill,
                    Map.of("input", input, "agentName", agent.getName()));
                if (required) {
                    approvalNotifier.pushPendingCount(approvalEngine.pendingCount());
                    results.add(resultMap(stage, "[等待审批: " + approvalSkill + "]"));
                    return results; // stop chain, wait for approval
                }
            }

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

    // ───── parallel ─────

    private List<Map<String, String>> executeParallel(ExecutionPlan plan, String contextId) {
        List<CompletableFuture<Map.Entry<Integer, Map<String, String>>>> futures = new ArrayList<>();

        for (int i = 0; i < plan.stages().size(); i++) {
            final Stage stage = plan.stages().get(i);
            final int idx = i;
            var future = CompletableFuture.supplyAsync(() -> {
                Agent agent = loadAgent(stage.agentId());
                if (agent == null) return Map.entry(idx, resultMap(stage, "[agent not found]"));
                try {
                    String input = stage.description();

                    // Approval check
                    String approvalSkill = checkApprovalNeeded(agent, stage);
                    if (approvalSkill != null) {
                        String taskId = createApprovalTask(contextId, agent, input);
                        boolean required = approvalEngine.requestApproval(taskId, agent.getId(), approvalSkill,
                            Map.of("input", input, "agentName", agent.getName()));
                        if (required) {
                            return Map.entry(idx, resultMap(stage, "[等待审批: " + approvalSkill + "]"));
                        }
                    }

                    Message msg = new Message("user", List.of(new TextPart(input)), contextId, null, null, null);
                    A2ATask task = a2aClient.sendMessage(agent, msg, true);
                    return Map.entry(idx, resultMap(stage, extractTaskOutput(task)));
                } catch (Exception e) {
                    log.error("parallel agent {} failed", agent.getName(), e);
                    return Map.entry(idx, resultMap(stage, "[error: " + e.getMessage() + "]"));
                }
            }, taskExecutor);
            futures.add(future);
        }

        return futures.stream()
            .map(CompletableFuture::join)
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    // ───── conditional ─────

    private List<Map<String, String>> executeConditional(ExecutionPlan plan, String contextId) {
        List<Map<String, String>> results = new ArrayList<>();
        String previousOutput = null;

        for (Stage stage : plan.stages()) {
            // Evaluate condition against previous output
            if (stage.condition() != null && !stage.condition().isBlank()) {
                if (!evaluateCondition(stage.condition(), previousOutput)) {
                    log.info("conditional: skipping stage {} (condition not met: {})", stage.agentName(), stage.condition());
                    continue;
                }
                log.info("conditional: condition met for stage {}", stage.agentName());
            }

            Agent agent = loadAgent(stage.agentId());
            if (agent == null) {
                results.add(resultMap(stage, "[agent not found]"));
                continue;
            }

            String input = "previous".equals(stage.inputFrom()) && previousOutput != null ? previousOutput : stage.description();
            log.info("conditional: calling agent {} ({})", agent.getName(), stage.agentId());

            // Approval check
            String approvalSkill = checkApprovalNeeded(agent, stage);
            if (approvalSkill != null) {
                String taskId = createApprovalTask(contextId, agent, input);
                boolean required = approvalEngine.requestApproval(taskId, agent.getId(), approvalSkill,
                    Map.of("input", input, "agentName", agent.getName()));
                if (required) {
                    approvalNotifier.pushPendingCount(approvalEngine.pendingCount());
                    results.add(resultMap(stage, "[等待审批: " + approvalSkill + "]"));
                    return results;
                }
            }

            try {
                Message msg = new Message("user", List.of(new TextPart(input)), contextId, null, null, null);
                A2ATask task = a2aClient.sendMessage(agent, msg, true);
                String output = extractTaskOutput(task);
                previousOutput = output;
                results.add(resultMap(stage, output));
            } catch (Exception e) {
                log.error("conditional agent {} failed", agent.getName(), e);
                results.add(resultMap(stage, "[error: " + e.getMessage() + "]"));
            }
        }
        return results;
    }

    // ───── router ─────

    private List<Map<String, String>> executeRouter(ExecutionPlan plan, String contextId) {
        if (plan.stages().isEmpty()) return List.of();
        Stage stage = plan.stages().get(0);
        Agent agent = loadAgent(stage.agentId());
        if (agent == null) return List.of(resultMap(stage, "[agent not found]"));

        // Approval check
        String approvalSkill = checkApprovalNeeded(agent, stage);
        if (approvalSkill != null) {
            String taskId = createApprovalTask(contextId, agent, stage.description());
            boolean required = approvalEngine.requestApproval(taskId, agent.getId(), approvalSkill,
                Map.of("input", stage.description(), "agentName", agent.getName()));
            if (required) {
                approvalNotifier.pushPendingCount(approvalEngine.pendingCount());
                return List.of(resultMap(stage, "[等待审批: " + approvalSkill + "]"));
            }
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

    // ───── approval helpers ─────

    /**
     * Check if the agent's skill requires approval.
     * @return skill name if approval needed, null otherwise
     */
    private String checkApprovalNeeded(Agent agent, Stage stage) {
        String approvalSkills = agent.getApprovalSkills();
        if (approvalSkills == null || approvalSkills.isBlank()) return null;

        try {
            List<String> skills = om.readValue(approvalSkills, new TypeReference<List<String>>() {});
            if (skills.isEmpty()) return null;

            // Match against stage description or role
            String desc = stage.description() == null ? "" : stage.description().toLowerCase();
            for (String skill : skills) {
                if (desc.contains(skill.toLowerCase())) {
                    return skill;
                }
            }
            // If agent declares approval skills but none matched description,
            // still check if there's a generic "*" skill
            if (skills.contains("*")) return "sensitive_operation";
        } catch (Exception e) {
            log.warn("failed to parse approvalSkills for agent {}: {}", agent.getName(), e.getMessage());
        }
        return null;
    }

    private String createApprovalTask(String contextId, Agent agent, String input) {
        Message userMsg = new Message("user", List.of(new TextPart(input)), contextId, null, null, null);
        A2ATask task = taskManagerService.create(contextId, agent.getId(), userMsg);
        return task.id();
    }

    // ───── condition evaluation ─────

    /**
     * Evaluate a condition against previous output.
     * Supported formats:
     *   "contains:keyword" — previous output contains keyword
     *   "not_contains:keyword" — previous output does NOT contain keyword
     *   "always" — always true (first stage default)
     */
    private boolean evaluateCondition(String condition, String previousOutput) {
        if (condition == null || condition.isBlank() || "always".equals(condition)) return true;
        String output = previousOutput == null ? "" : previousOutput.toLowerCase();

        if (condition.startsWith("contains:")) {
            return output.contains(condition.substring("contains:".length()).toLowerCase());
        }
        if (condition.startsWith("not_contains:")) {
            return !output.contains(condition.substring("not_contains:".length()).toLowerCase());
        }
        // Default: treat as contains
        return output.contains(condition.toLowerCase());
    }

    // ───── common helpers ─────

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