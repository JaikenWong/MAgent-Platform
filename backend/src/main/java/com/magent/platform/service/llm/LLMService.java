package com.magent.platform.service.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.dto.orchestrator.Stage;
import com.magent.platform.entity.Agent;
import com.magent.platform.entity.OrchestrationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 服务: 使用 Spring AI 做意图解析, 将用户需求拆解为多 Agent 执行计划.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ChatClient chatClient;
    private final ObjectMapper om;

    public ExecutionPlan parseIntent(String userMessage, List<Agent> agents, List<OrchestrationRule> rules) {
        String systemPrompt = buildSystemPrompt(agents, rules);
        String userPrompt = userMessage;

        var prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userPrompt)
        ));

        try {
            var response = chatClient.call(prompt);
            String content = response.getResults().get(0).getOutput().getContent();
            return parsePlan(content, agents);
        } catch (Exception e) {
            log.error("LLM intent parse failed", e);
            return fallbackPlan(agents, userMessage);
        }
    }

    public String synthesize(String question, List<Map<String, String>> agentResults) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < agentResults.size(); i++) {
            Map<String, String> r = agentResults.get(i);
            ctx.append("Agent ").append(r.get("agentName")).append(": ").append(r.get("output")).append("\n");
        }
        String prompt = """
            You are an orchestrator summarizing results from multiple agents.
            Synthesize the following outputs into a coherent, helpful response for the user.

            User question: %s

            Agent outputs:
            %s

            Provide a clear, well-structured summary.
            """.formatted(question, ctx.toString());

        try {
            var response = chatClient.call(new Prompt(new UserMessage(prompt)));
            return response.getResults().get(0).getOutput().getContent();
        } catch (Exception e) {
            log.warn("synthesis failed, returning raw concatenation", e);
            return ctx.toString();
        }
    }

    private String buildSystemPrompt(List<Agent> agents, List<OrchestrationRule> rules) {
        StringBuilder sb = new StringBuilder("""
            You are an orchestrator for a multi-agent platform.
            Your job is to break down user requests into an execution plan involving one or more agents.

            Available agents:
            """);

        for (Agent a : agents) {
            sb.append("- ID: ").append(a.getId())
              .append(", Name: ").append(a.getName())
              .append(", Description: ").append(a.getDescription() == null ? "N/A" : a.getDescription());

            if (a.getSkills() != null && !a.getSkills().isBlank()) {
                sb.append(", Skills: ").append(a.getSkills());
            }
            sb.append("\n");
        }

        if (rules != null && !rules.isEmpty()) {
            sb.append("\nOrchestration rules (prefer these when applicable):\n");
            for (OrchestrationRule r : rules) {
                sb.append("- ").append(r.getName()).append(": ").append(r.getDescription() == null ? "N/A" : r.getDescription()).append("\n");
            }
        }

        sb.append("""

            Respond with a JSON object:
            {
              "mode": "sequential|parallel|conditional|router",
              "reasoning": "why you chose this plan",
              "stages": [
                {"agentId": "uuid", "agentName": "name", "inputFrom": "user|previous", "description": "what this agent should do"}
              ]
            }

            Rules:
            - "sequential": agents run one after another, output feeds into next
            - "parallel": agents run concurrently on the same input
            - "conditional": the next agent depends on previous agent output
            - "router": pick the single best agent for the task
            - Use the minimum number of agents needed
            - Only output JSON, no other text
            """);

        return sb.toString();
    }

    private ExecutionPlan parsePlan(String content, List<Agent> agents) {
        try {
            // Strip markdown code fences if present
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf('\n') + 1);
                if (json.endsWith("```")) {
                    json = json.substring(0, json.lastIndexOf("```"));
                }
                json = json.trim();
            }

            Map<String, Object> parsed = om.readValue(json, new TypeReference<Map<String, Object>>() {});
            String mode = (String) parsed.getOrDefault("mode", "sequential");
            String reasoning = (String) parsed.getOrDefault("reasoning", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawStages = (List<Map<String, Object>>) parsed.get("stages");
            List<Stage> stages = rawStages.stream().map(s -> new Stage(
                (String) s.get("agentId"),
                (String) s.get("agentName"),
                (String) s.getOrDefault("inputFrom", "user"),
                (String) s.getOrDefault("description", ""),
                rawStages.indexOf(s)
            )).collect(Collectors.toList());

            return ExecutionPlan.of(mode, stages, reasoning);
        } catch (Exception e) {
            log.error("failed to parse LLM response as execution plan", e);
            return fallbackPlan(agents, content);
        }
    }

    private ExecutionPlan fallbackPlan(List<Agent> agents, String userMessage) {
        // Fallback: assign the first active agent with the user message
        if (agents.isEmpty()) {
            return ExecutionPlan.of("sequential", List.of(), "no agents available");
        }
        Agent first = agents.get(0);
        return ExecutionPlan.of("sequential",
            List.of(new Stage(first.getId(), first.getName(), "user", userMessage, 0)),
            "fallback: sent to first available agent");
    }
}