package com.helphub.identity.user.dto;

import com.helphub.identity.user.entity.Gender;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicUserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        Gender gender,
        UUID avatarMediaId,
        String roleCode,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt) {
}