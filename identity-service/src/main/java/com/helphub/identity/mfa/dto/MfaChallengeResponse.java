package com.helphub.identity.mfa.dto;

import java.util.UUID;

public record MfaChallengeResponse(
        UUID challengeId,
        String method,
        long expiresIn) {
}