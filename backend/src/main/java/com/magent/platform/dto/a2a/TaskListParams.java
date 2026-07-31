package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/** params for `tasks/list`. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskListParams(String contextId, String state, Integer limit) {}