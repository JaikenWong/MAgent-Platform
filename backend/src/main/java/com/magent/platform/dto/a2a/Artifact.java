package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Artifact(
    String artifactId,
    String name,
    String description,
    List<Part> parts
) {
    public Artifact(String artifactId, List<Part> parts) {
        this(artifactId, null, null, parts);
    }
}