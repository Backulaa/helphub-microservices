package com.helphub.media.common.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> success(
            String code,
            String message,
            T data) {

        return new ApiResponse<>(
                true,
                code,
                message,
                data,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static ApiResponse<Void> error(
            String code,
            String message) {

        return new ApiResponse<>(
                false,
                code,
                message,
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
