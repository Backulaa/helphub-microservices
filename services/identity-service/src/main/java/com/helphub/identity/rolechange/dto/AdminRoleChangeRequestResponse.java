
package com.helphub.identity.rolechange.dto;

import com.helphub.identity.rolechange.entity.RoleRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRoleChangeRequestResponse(

        UUID id,

        UUID userId,

        String fullName,

        String email,

        String currentRole,

        String requestedRole,

        String actualRole,

        RoleRequestStatus status,

        String reason,

        String reviewNote,

        UUID reviewedById,

        OffsetDateTime createdAt,

        OffsetDateTime reviewedAt,

        OffsetDateTime cancelledAt

) {
}
