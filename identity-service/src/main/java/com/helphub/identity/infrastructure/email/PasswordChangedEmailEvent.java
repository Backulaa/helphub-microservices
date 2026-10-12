package com.helphub.identity.infrastructure.email;

import java.time.OffsetDateTime;

public record PasswordChangedEmailEvent(
        String email,
        OffsetDateTime changedAt) {
}