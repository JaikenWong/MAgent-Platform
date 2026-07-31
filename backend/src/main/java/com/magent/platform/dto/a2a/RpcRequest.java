package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcRequest(
    String jsonrpc,
    Object id,
    String method,
    Object params
) {
    public static RpcRequest create(String method, Object params) {
        return new RpcRequest("2.0", System.currentTimeMillis(), method, params);
    }

    public <T> T paramsAs(Class<T> cls, com.fasterxml.jackson.databind.ObjectMapper om) {
        return om.convertValue(params, cls);
    }
}