package com.magent.platform.dto.orchestrator;

import java.util.List;

public record ExecutionPlan(
    String executionMode,
    List<Stage> stages,
    String reasoning
) {
    public static ExecutionPlan of(String executionMode, List<Stage> stages, String reasoning) {
        return new ExecutionPlan(executionMode, stages, reasoning);
    }
}