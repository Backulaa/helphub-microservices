package com.helphub.media.media.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.dto.CompleteUploadResponse;
import com.helphub.media.media.service.MediaPrivateUploadService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaPrivateUploadController {

    private final MediaPrivateUploadService service;

    public MediaPrivateUploadController(
            MediaPrivateUploadService service) {

        this.service = service;
    }

    @PostMapping(value = "/{mediaId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CompleteUploadResponse>> uploadPrivate(
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {

        UUID ownerId = UUID.fromString(
                jwt.getSubject());

        CompleteUploadResponse response = service.upload(
                mediaId,
                ownerId,
                file);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "PRIVATE_MEDIA_READY",
                        "Private media uploaded successfully",
                        response));
    }
}
