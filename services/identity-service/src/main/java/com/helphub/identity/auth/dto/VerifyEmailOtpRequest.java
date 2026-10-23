package com.helphub.identity.auth.dto;

import jakarta.validation.constraints.*;

public record VerifyEmailOtpRequest(

        @NotBlank @Email String email,

        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp

) {
}