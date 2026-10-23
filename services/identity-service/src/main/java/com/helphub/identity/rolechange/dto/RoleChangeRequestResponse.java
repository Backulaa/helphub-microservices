
package com.helphub.identity.rolechange.dto;

import com.helphub.identity.rolechange.entity.RoleRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleChangeRequestResponse(

        UUID id,

        String currentRole,

        String requestedRole,

        RoleRequestStatus status,

        String reason,

        String reviewNote,

        OffsetDateTime createdAt,

        OffsetDateTime reviewedAt,

        OffsetDateTime cancelledAt

) {
}
