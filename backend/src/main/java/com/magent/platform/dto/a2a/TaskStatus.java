package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStatus(
    TaskState state,
    String timestamp,
    Message message
) {
    @JsonValue
    public Object toJson() {
        if (message == null) {
            return java.util.Map.of("state", state.name().toLowerCase(), "timestamp", timestamp == null ? "" : timestamp);
        }
        return java.util.Map.of("state", state.name().toLowerCase(), "timestamp", timestamp == null ? "" : timestamp, "message", message);
    }
}