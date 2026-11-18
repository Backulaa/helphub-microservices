package com.helphub.media.media.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.dto.CompleteUploadRequest;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.service.MediaCompleteUploadService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaCompleteUploadController {

    private final MediaCompleteUploadService service;

    public MediaCompleteUploadController(
            MediaCompleteUploadService service) {

        this.service = service;
    }

    @PostMapping("/{mediaId}/complete")
    public ResponseEntity<ApiResponse<CompleteUploadResponse>> completeUpload(
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CompleteUploadRequest request) {

        UUID ownerId = UUID.fromString(
                jwt.getSubject());

        CompleteUploadResponse response = service.complete(
                mediaId,
                ownerId,
                request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_READY",
                        "Media upload completed successfully",
                        response));
    }
}
