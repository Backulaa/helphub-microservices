package com.helphub.identity.mfa.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaPasswordRequest(
        @NotBlank String currentPassword) {
}