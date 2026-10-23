package com.helphub.identity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank @Email String email,

        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain exactly 6 digits") String otp,

        @NotBlank String newPassword) {
}