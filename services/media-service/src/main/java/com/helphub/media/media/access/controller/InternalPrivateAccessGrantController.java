package com.helphub.media.media.access.controller;

import com.helphub.media.common.dto.ApiResponse;
import com.helphub.media.media.access.dto.*;
import com.helphub.media.media.access.service.PrivateAccessGrantService;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/media/access-grants")
public class InternalPrivateAccessGrantController {

    private final PrivateAccessGrantService service;

    public InternalPrivateAccessGrantController(
            PrivateAccessGrantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PrivateAccessGrantResponse>> issue(
            Authentication authentication,
            @Valid @RequestBody PrivateAccessGrantRequest request) {

        PrivateAccessGrantResponse result = service.issue(
                authentication.getName(),
                request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .body(
                        ApiResponse.success(
                                "PRIVATE_ACCESS_GRANTED",
                                "Temporary private media access granted",
                                result));
    }
}
