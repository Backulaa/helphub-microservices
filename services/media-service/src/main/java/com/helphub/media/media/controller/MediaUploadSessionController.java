package com.helphub.media.media.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.dto.CreateUploadSessionRequest;
import com.helphub.media.media.dto.UploadSessionResponse;
import com.helphub.media.media.service.MediaUploadSessionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaUploadSessionController {

    private final MediaUploadSessionService service;

    public MediaUploadSessionController(
            MediaUploadSessionService service) {

        this.service = service;
    }

    @PostMapping("/upload-sessions")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> createUploadSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUploadSessionRequest request) {

        UUID ownerId = UUID.fromString(
                jwt.getSubject());

        UploadSessionResponse response = service.createSession(
                ownerId,
                request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "UPLOAD_SESSION_CREATED",
                        "Upload session created successfully",
                        response));
    }
}
