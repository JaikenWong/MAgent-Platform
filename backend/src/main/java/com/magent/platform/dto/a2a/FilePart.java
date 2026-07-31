package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("file")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilePart(
    String type,
    FileRef file
) implements Part {
    public record FileRef(
        String name,
        String mimeType,
        byte[] bytes,  // 与 uri 二选一 (base64)
        String uri
    ) {}

    public FilePart(FileRef file) { this("file", file); }
}