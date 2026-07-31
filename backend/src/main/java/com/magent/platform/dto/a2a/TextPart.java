package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("text")
public record TextPart(String type, String text) implements Part {
    public TextPart(String text) { this("text", text); }
}