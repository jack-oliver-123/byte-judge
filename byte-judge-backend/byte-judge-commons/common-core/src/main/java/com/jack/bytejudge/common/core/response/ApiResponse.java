package com.jack.bytejudge.common.core.response;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public record ApiResponse<T>(int code, T data, String message, String requestId, String serverTime) {
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, data, "OK", requestId, now());
    }

    public static ApiResponse<Void> failure(ApiError error, String requestId) {
        return new ApiResponse<>(error.code(), null, error.message(), requestId, now());
    }

    public static <T> ApiResponse<T> failure(ApiError error, T data, String requestId) {
        return new ApiResponse<>(error.code(), data, error.message(), requestId, now());
    }

    private static String now() {
        return UTC_MILLIS.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
