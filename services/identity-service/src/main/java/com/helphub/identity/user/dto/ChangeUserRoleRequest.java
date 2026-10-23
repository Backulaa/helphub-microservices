package com.helphub.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeUserRoleRequest(

        @NotBlank(message = "Role code is required") @Pattern(regexp = "^(REQUESTER|VOLUNTEER|COLLABORATOR|ADMIN)$", message = "Unsupported role code") String roleCode

) {
}