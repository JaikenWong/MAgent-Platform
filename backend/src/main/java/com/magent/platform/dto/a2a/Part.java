package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = FilePart.class, name = "file"),
    @JsonSubTypes.Type(value = DataPart.class, name = "data"),
})
public sealed interface Part permits TextPart, FilePart, DataPart {
    String type();
}