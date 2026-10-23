package com.helphub.identity.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank @Size(max = 50) String fullName,

        @NotBlank @Email @Size(max = 255) String email,

        @NotNull String password,

        @Size(max = 20) @Pattern(regexp = "^\\+?[0-9]{9,15}$") String phoneNumber,

        @NotBlank String roleCode

) {
}