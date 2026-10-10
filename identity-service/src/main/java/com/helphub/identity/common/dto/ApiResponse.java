package com.helphub.identity.common.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        OffsetDateTime timestamp) {

    // Successful response with data
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

    // Successful response without data
    public static ApiResponse<Void> success(
            String code,
            String message) {
        return new ApiResponse<>(
                true,
                code,
                message,
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    // Failed response without data
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

    // Failed response with additional details
    public static <T> ApiResponse<T> error(
            String code,
            String message,
            T data) {
        return new ApiResponse<>(
                false,
                code,
                message,
                data,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}