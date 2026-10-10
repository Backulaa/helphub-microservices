package com.helphub.identity.auth.dto;

import java.util.UUID;

public record TokenResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UUID sessionId) {
}