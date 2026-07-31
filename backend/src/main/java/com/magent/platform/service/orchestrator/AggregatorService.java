package com.magent.platform.service.orchestrator;

import com.magent.platform.dto.orchestrator.ExecutionPlan;
import com.magent.platform.service.llm.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregator: 合并多 Agent 输出为最终回复.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatorService {

    private final LLMService llmService;

    /**
     * 聚合多 Agent 结果.
     * 简单场景直接拼装, 复杂场景调 LLM 合成.
     */
    public String aggregate(List<Map<String, String>> agentResults, ExecutionPlan plan, String userMessage) {
        if (agentResults.isEmpty()) {
            return "No agents were available to handle your request.";
        }

        if (agentResults.size() == 1) {
            return agentResults.get(0).get("output");
        }

        // for sequential/parallel with multiple agents, use LLM synthesis
        if (plan.stages().size() > 2) {
            return llmService.synthesize(userMessage, agentResults);
        }

        // simple case: just concatenate with attribution
        return agentResults.stream()
            .map(r -> "**" + r.get("agentName") + "**: " + r.get("output"))
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}