package com.helphub.media.media.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.dto.*;
import com.helphub.media.media.service.MediaMetadataService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaMetadataController {

    private final MediaMetadataService service;

    public MediaMetadataController(
            MediaMetadataService service) {
        this.service = service;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<MediaMetadataResponse>> getMedia(
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID requesterId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_FOUND",
                        "Media metadata retrieved successfully",
                        service.get(mediaId, requesterId)));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<MediaMetadataResponse>>> getBatch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MediaBatchRequest request) {

        UUID requesterId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_BATCH_RETRIEVED",
                        "Media metadata batch retrieved successfully",
                        service.getBatch(
                                request.mediaIds(),
                                requesterId)));
    }
}
