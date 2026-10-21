package com.pointbank.bankplatform.dto;

import java.time.Instant;

public record ApiResponseDto<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(true, message, data, Instant.now());
    }
}
