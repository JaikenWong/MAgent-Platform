package com.magent.platform.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcError(int code, String message, Object data) {
    public static final int PARSE_ERROR      = -32700;
    public static final int INVALID_REQUEST  = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS   = -32602;
    public static final int INTERNAL_ERROR   = -32603;
    public static final int TASK_NOT_FOUND      = -32001;
    public static final int TASK_NOT_CANCELABLE = -32002;
    public static final int UNSUPPORTED_OP    = -32003;
    public static final int CONTENT_TYPE_NOT_SUPPORTED = -32005;
}