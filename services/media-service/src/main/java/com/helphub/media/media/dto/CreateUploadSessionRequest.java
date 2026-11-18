
package com.helphub.media.media.dto;

import com.helphub.media.media.entity.MediaVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadSessionRequest(

        @NotBlank @Size(max = 255) String filename,

        @NotBlank String mimeType,

        @NotNull @Positive Long sizeBytes,

        @NotNull MediaVisibility visibility

) {
}
