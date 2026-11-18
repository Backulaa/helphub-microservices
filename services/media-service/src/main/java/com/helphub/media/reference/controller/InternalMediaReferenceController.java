package com.helphub.media.reference.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.reference.dto.*;
import com.helphub.media.reference.service.MediaReferenceService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/media/references")
public class InternalMediaReferenceController {

    private final MediaReferenceService service;

    public InternalMediaReferenceController(
            MediaReferenceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MediaReferenceResponse>> attach(
            Authentication authentication,
            @Valid @RequestBody MediaReferenceRequest request) {

        MediaReferenceResponse result = service.attach(
                authentication.getName(),
                request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_REFERENCE_REGISTERED",
                        "Media reference registered successfully",
                        result));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> detach(
            Authentication authentication,
            @Valid @RequestBody MediaReferenceDeleteRequest request) {

        boolean removed = service.detach(
                authentication.getName(),
                request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_REFERENCE_REMOVED",
                        "Media reference removal processed",
                        removed));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UUID>>> getByResource(
            Authentication authentication,
            @RequestParam String resourceType,
            @RequestParam UUID resourceId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MEDIA_REFERENCES_FOUND",
                        "Media references retrieved successfully",
                        service.getByResource(
                                authentication.getName(),
                                resourceType,
                                resourceId)));
    }
}
