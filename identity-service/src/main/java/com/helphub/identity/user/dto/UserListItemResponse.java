package com.helphub.identity.user.dto;

import com.helphub.identity.user.entity.AccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserListItemResponse(
        UUID id,
        String fullName,
        String email,
        String roleCode,
        AccountStatus status,
        boolean emailVerified,
        OffsetDateTime createdAt) {
}