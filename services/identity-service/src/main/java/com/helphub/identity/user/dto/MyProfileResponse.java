package com.helphub.identity.user.dto;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.Gender;
import com.helphub.identity.user.entity.MfaMethod;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MyProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        Gender gender,
        UUID avatarMediaId,
        String roleCode,
        AccountStatus status,
        boolean emailVerified,
        boolean mfaEnabled,
        MfaMethod mfaMethod,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt) {
}