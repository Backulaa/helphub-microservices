package com.helphub.identity.user.dto;

import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.Gender;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt) {
}