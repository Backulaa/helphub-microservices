package com.helphub.identity.rolechange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleChangeRequestRequest(

        @NotBlank(message = "Requested role is required") String requestedRole,

        @Size(max = 1000, message = "Reason must not exceed 1000 characters") String reason

) {
}