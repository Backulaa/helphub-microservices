package com.helphub.identity.mfa.dto;

import com.helphub.identity.user.entity.MfaMethod;

import java.time.OffsetDateTime;

public record MfaStatusResponse(
        boolean enabled,
        MfaMethod method,
        OffsetDateTime enabledAt) {
}