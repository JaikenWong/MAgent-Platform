package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataPart(
    String type,
    Object data,
    Object schema
) implements Part {
    public DataPart(Object data) { this("data", data, null); }
}