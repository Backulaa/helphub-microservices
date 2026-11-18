package com.helphub.media.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteUploadRequest(

        @NotBlank String assetId,

        @NotBlank String publicId,

        @NotNull @Positive Long version

) {
}
