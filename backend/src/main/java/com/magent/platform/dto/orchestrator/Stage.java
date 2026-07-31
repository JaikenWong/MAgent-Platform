package com.magent.platform.dto.orchestrator;

public record Stage(
    String agentId,
    String agentName,
    String inputFrom,
    String description,
    int order,
    String condition
) {
    public Stage(String agentId, String agentName, String inputFrom, String description, int order) {
        this(agentId, agentName, inputFrom, description, order, null);
    }
}