package com.pointbank.bankplatform.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
