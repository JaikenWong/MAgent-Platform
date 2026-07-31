package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2ATask(
    String id,
    String contextId,
    TaskStatus status,
    List<Artifact> artifacts,
    List<Message> history,
    String kind
) {
    public A2ATask(String id, String contextId, TaskStatus status) {
        this(id, contextId, status, null, null, "task");
    }
}