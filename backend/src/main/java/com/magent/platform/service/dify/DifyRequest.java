package com.magent.platform.service.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/** Dify 一次调用请求. 兼容 Chatflow (chat-messages) 与 Workflow (workflows/run). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DifyRequest(
    String inputs,          // JSON string of inputs map, 或 null
    String query,
    String user,
    String conversationId,
    List<Map<String, Object>> files,
    String responseMode     // streaming | blocking
) {
    public boolean isChat() { return query != null && !query.isBlank(); }
}