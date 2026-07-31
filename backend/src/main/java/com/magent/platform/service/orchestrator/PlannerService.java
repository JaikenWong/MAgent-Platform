package com.magent.platform.service.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.dto.orchestrator.Stage;
import com.magent.platform.entity.Agent;
import com.magent.platform.entity.OrchestrationRule;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.mapper.OrchestrationRuleMapper;
import com.magent.platform.service.llm.LLMService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Planner: 规则匹配 + LLM 意图解析 → ExecutionPlan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final AgentMapper agentMapper;
    private final OrchestrationRuleMapper ruleMapper;
    private final LLMService llmService;
    private final ObjectMapper om;

    public ExecutionPlan plan(String userMessage) {
        List<Agent> agents = loadActiveAgents();
        List<OrchestrationRule> rules = loadEnabledRules();

        // 1. Try rule matching first (keyword -> regex -> intent)
        OrchestrationRule matched = matchRule(userMessage, rules);
        if (matched != null) {
            log.info("rule matched: {} (type={})", matched.getName(), matched.getTriggerType());
            return buildPlanFromRule(matched, agents);
        }

        // 2. Fallback: LLM intent parsing
        log.info("no rule matched, using LLM intent parsing");
        return llmService.parseIntent(userMessage, agents, rules);
    }

    private List<Agent> loadActiveAgents() {
        QueryWrapper<Agent> qw = new QueryWrapper<>();
        qw.eq("status", "active");
        return agentMapper.selectList(qw);
    }

    private List<OrchestrationRule> loadEnabledRules() {
        QueryWrapper<OrchestrationRule> qw = new QueryWrapper<>();
        qw.eq("enabled", true);
        qw.orderByAsc("priority");
        return ruleMapper.selectList(qw);
    }

    private OrchestrationRule matchRule(String message, List<OrchestrationRule> rules) {
        for (OrchestrationRule rule : rules) {
            if (rule.getTriggerConfig() == null || rule.getTriggerConfig().isBlank()) continue;

            try {
                Map<String, Object> config = om.readValue(rule.getTriggerConfig(), new TypeReference<Map<String, Object>>() {});
                String triggerType = rule.getTriggerType();

                if ("keyword".equals(triggerType) && matchKeyword(message, config)) return rule;
                if ("regex".equals(triggerType) && matchRegex(message, config)) return rule;
                if ("all".equals(triggerType)) return rule;
            } catch (Exception e) {
                log.warn("failed to parse trigger config for rule {}", rule.getName(), e);
            }
        }
        return null;
    }

    private boolean matchKeyword(String msg, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) config.get("keywords");
        if (keywords == null) return false;
        String lower = msg.toLowerCase();
        return keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    private boolean matchRegex(String msg, Map<String, Object> config) {
        String pattern = (String) config.get("regex");
        if (pattern == null) return false;
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(msg).find();
        } catch (Exception e) {
            log.warn("invalid regex pattern: {}", pattern, e);
            return false;
        }
    }

    private ExecutionPlan buildPlanFromRule(OrchestrationRule rule, List<Agent> agents) {
        List<Stage> stages = new ArrayList<>();
        if (rule.getAgentChain() != null && !rule.getAgentChain().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> chain = om.readValue(rule.getAgentChain(),
                    new TypeReference<List<Map<String, Object>>>() {});
                for (int i = 0; i < chain.size(); i++) {
                    Map<String, Object> link = chain.get(i);
                    String agentId = (String) link.get("agentId");
                    String role = (String) link.getOrDefault("role", "");
                    String inputFrom = (String) link.getOrDefault("inputFrom", i == 0 ? "user" : "previous");

                    Agent agent = agents.stream().filter(a -> a.getId().equals(agentId)).findFirst().orElse(null);
                    String name = agent != null ? agent.getName() : agentId;
                    stages.add(new Stage(agentId, name, inputFrom, role, i));
                }
            } catch (Exception e) {
                log.warn("failed to parse agentChain, falling back to fallback agent", e);
            }
        }

        if (stages.isEmpty() && rule.getFallbackAgentId() != null) {
            Agent fallback = agents.stream().filter(a -> a.getId().equals(rule.getFallbackAgentId())).findFirst().orElse(null);
            if (fallback != null) {
                stages.add(new Stage(fallback.getId(), fallback.getName(), "user", "fallback", 0));
            }
        }

        return ExecutionPlan.of(rule.getExecutionMode(), stages, "matched rule: " + rule.getName());
    }
}