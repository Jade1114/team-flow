package com.example.server.common;

public record ApiResponse<T>(boolean success, T data, String message, String code, Object details) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", null, null);
    }

    public static ApiResponse<Object> fail(String code, String message) {
        return new ApiResponse<>(false, null, message, code, null);
    }
}
