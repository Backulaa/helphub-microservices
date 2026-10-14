package com.helphub.identity.user.dto;

import com.helphub.identity.user.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(

        @NotNull(message = "Account status is required") AccountStatus status

) {
}