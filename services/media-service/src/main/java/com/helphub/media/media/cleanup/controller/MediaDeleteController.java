package com.helphub.media.media.cleanup.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.cleanup.dto.MediaDeleteResponse;
import com.helphub.media.media.cleanup.service.MediaDeleteService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaDeleteController {

    private final MediaDeleteService service;

    public MediaDeleteController(MediaDeleteService service) {
        this.service = service;
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<MediaDeleteResponse>> delete(
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID requesterId = UUID.fromString(jwt.getSubject());

        MediaDeleteResponse result = service.delete(mediaId, requesterId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.success(
                                "MEDIA_DELETION_SCHEDULED",
                                "Media deletion scheduled successfully",
                                result));
    }
}
