package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/** params for `tasks/get` / `tasks/cancel` / `tasks/subscribe`. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskIdParams(String id, Integer historyLength) {}