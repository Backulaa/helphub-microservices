package com.helphub.identity.mfa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record VerifyMfaRequest(
        @NotNull UUID challengeId,

        @Pattern(regexp = "^[0-9]{6}$") String otp) {
}