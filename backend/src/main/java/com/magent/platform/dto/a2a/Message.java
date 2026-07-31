package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
    String role,          // user | agent
    List<Part> parts,
    String contextId,
    String taskId,
    String messageId,
    String kind
) {
    public Message(String role, List<Part> parts) {
        this(role, parts, null, null, null, "message");
    }
}