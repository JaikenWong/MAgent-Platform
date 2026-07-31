package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcResponse(
    String jsonrpc,
    Object id,
    Object result,
    RpcError error
) {
    public static RpcResponse ok(Object id, Object result) {
        return new RpcResponse("2.0", id, result, null);
    }
    public static RpcResponse err(Object id, RpcError e) {
        return new RpcResponse("2.0", id, null, e);
    }
}