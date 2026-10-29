package com.pointbank.bankplatform.exception;

import java.time.Instant;
import java.util.Map;

import com.pointbank.bankplatform.dto.ErrorDetails;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        ErrorDetails details,
        Map<String, String> validationErrors
) {
}
