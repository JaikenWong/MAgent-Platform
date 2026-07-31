package com.magent.platform.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record R<T>(int code, String msg, T data) {

    public static <T> R<T> ok(T data) {
        return new R<>(0, "OK", data);
    }

    public static <T> R<T> ok() {
        return new R<>(0, "OK", null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }
}