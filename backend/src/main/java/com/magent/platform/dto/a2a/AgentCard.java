package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentCard(
    String name,
    String description,
    String version,
    String protocolVersion,
    String url,
    AgentCapabilities capabilities,
    List<AgentSkill> skills,
    List<String> defaultInputModes,
    List<String> defaultOutputModes
) {
    public record AgentCapabilities(
        Boolean streaming,
        Boolean pushNotifications,
        Boolean stateTransitionHistory
    ) {}

    public record AgentSkill(
        String id,
        String name,
        String description,
        List<String> tags,
        List<String> examples,
        List<String> inputModes,
        List<String> outputModes
    ) {}
}