package com.helphub.identity.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssuedTokens(
                String accessToken,
                String refreshToken,
                long expiresIn,
                UUID sessionId,
                OffsetDateTime sessionExpiresAt) {
}