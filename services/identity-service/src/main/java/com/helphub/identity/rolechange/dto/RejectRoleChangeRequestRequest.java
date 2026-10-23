package com.helphub.identity.rolechange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRoleChangeRequestRequest(

        @NotBlank(message = "Review note is required") @Size(max = 1000, message = "Review note must not exceed 1000 characters") String reviewNote

) {
}