package com.helphub.media.reference.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record MediaReferenceDeleteRequest(
        @NotNull UUID mediaId,

        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String resourceType,

        @NotNull UUID resourceId) {
}
