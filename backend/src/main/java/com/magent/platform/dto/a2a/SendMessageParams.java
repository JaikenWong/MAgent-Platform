package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** params for `message/send` and `message/stream`. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageParams(
    Message message,
    SendMessageConfiguration configuration
) {
    public record SendMessageConfiguration(
        List<String> acceptedOutputModes,
        Boolean blocking,
        Integer historyLength
    ) {}
}