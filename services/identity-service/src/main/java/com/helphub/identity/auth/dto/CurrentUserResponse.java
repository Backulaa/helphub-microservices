package com.helphub.identity.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        boolean emailVerified,
        boolean mfaEnabled) {
}