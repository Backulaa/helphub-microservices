package com.helphub.identity.infrastructure.email;

import java.time.OffsetDateTime;

public record PasswordResetEmailEvent(
        String email,
        String otp,
        OffsetDateTime expiresAt) {
}