package com.helphub.identity.infrastructure.email;

import java.time.OffsetDateTime;

public record OtpEmailEvent(
        String recipient,
        String otp,
        OffsetDateTime expiresAt) {
}