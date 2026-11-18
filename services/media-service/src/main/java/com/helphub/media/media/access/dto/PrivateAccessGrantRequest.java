package com.helphub.media.media.access.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record PrivateAccessGrantRequest(
        @NotNull UUID mediaId,

        @NotNull UUID viewerId,

        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") @NotNull String resourceType,

        @NotNull UUID resourceId) {
}
