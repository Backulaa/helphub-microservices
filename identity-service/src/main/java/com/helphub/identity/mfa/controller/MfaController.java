package com.helphub.identity.mfa.controller;

import com.helphub.identity.auth.dto.TokenResponse;
import com.helphub.identity.auth.service.IssuedTokens;

import com.helphub.identity.common.dto.ApiResponse;

import com.helphub.identity.infrastructure.security.RefreshCookieService;

import com.helphub.identity.mfa.dto.*;
import com.helphub.identity.mfa.entity.MfaChallenge;
import com.helphub.identity.mfa.entity.MfaChallengePurpose;
import com.helphub.identity.mfa.service.MfaOtpService;
import com.helphub.identity.mfa.service.MfaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Multi-Factor Authentication", description = "Email OTP MFA management and login verification")
@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final MfaOtpService mfaOtpService;
    private final RefreshCookieService refreshCookieService;

    public MfaController(
            MfaService mfaService,
            MfaOtpService mfaOtpService,
            RefreshCookieService refreshCookieService) {
        this.mfaService = mfaService;
        this.mfaOtpService = mfaOtpService;
        this.refreshCookieService = refreshCookieService;
    }

    // ==========================================
    // GET MFA Status
    // ==========================================

    @Operation(summary = "Get MFA status")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> status(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);

        MfaStatusResponse result = mfaService.getMfaStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MFA_STATUS_FETCHED",
                        "MFA status retrieved successfully",
                        result));
    }

    // ==========================================
    // Start MFA Setup
    // ==========================================

    @Operation(summary = "Start MFA setup")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/setup/start")
    public ResponseEntity<ApiResponse<MfaChallengeResponse>> startSetup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MfaPasswordRequest request) {
        MfaChallenge challenge = mfaService.start(
                userId(jwt),
                sessionId(jwt),
                request.currentPassword(),
                MfaChallengePurpose.SETUP);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MFA_SETUP_STARTED",
                        "Verification code sent to your email",
                        toResponse(challenge)));
    }

    // ==========================================
    // Confirm MFA Setup
    // ==========================================

    @Operation(summary = "Confirm and enable MFA")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/setup/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmSetup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyMfaRequest request) {
        boolean confirmed = mfaService.confirm(
                userId(jwt),
                sessionId(jwt),
                request.challengeId(),
                request.otp(),
                MfaChallengePurpose.SETUP);

        if (!confirmed) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            "INVALID_MFA_CHALLENGE",
                            "MFA verification failed"));
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieService.clear())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                        ApiResponse.success(
                                "MFA_ENABLED",
                                "MFA enabled successfully. Please log in again."));
    }

    // ==========================================
    // Verify MFA Login
    // ==========================================

    @Operation(summary = "Complete MFA login with email OTP")
    @PostMapping("/login/verify")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyLogin(
            @Valid @RequestBody VerifyMfaRequest request,

            @RequestHeader("X-XSRF-TOKEN") String csrfToken) {
        IssuedTokens issued = mfaService.verifyLogin(
                request.challengeId(),
                request.otp());

        if (issued == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            ApiResponse.<TokenResponse>error(
                                    "INVALID_MFA_CHALLENGE",
                                    "Invalid, expired or already used MFA challenge",
                                    null));
        }

        TokenResponse response = new TokenResponse(
                "Bearer",
                issued.accessToken(),
                issued.expiresIn(),
                issued.sessionId());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieService.create(issued))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                        ApiResponse.success(
                                "MFA_LOGIN_SUCCESS",
                                "MFA authentication completed successfully",
                                response));
    }

    // ==========================================
    // Start MFA Disable
    // ==========================================

    @Operation(summary = "Start MFA disabling")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/disable/start")
    public ResponseEntity<ApiResponse<MfaChallengeResponse>> startDisable(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MfaPasswordRequest request) {
        MfaChallenge challenge = mfaService.start(
                userId(jwt),
                sessionId(jwt),
                request.currentPassword(),
                MfaChallengePurpose.DISABLE);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "MFA_DISABLE_STARTED",
                        "Verification code sent to your email",
                        toResponse(challenge)));
    }

    // ==========================================
    // Confirm MFA Disable
    // ==========================================

    @Operation(summary = "Confirm and disable MFA")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/disable/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmDisable(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyMfaRequest request) {
        boolean confirmed = mfaService.confirm(
                userId(jwt),
                sessionId(jwt),
                request.challengeId(),
                request.otp(),
                MfaChallengePurpose.DISABLE);

        if (!confirmed) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            "INVALID_MFA_CHALLENGE",
                            "MFA verification failed"));
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieService.clear())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                        ApiResponse.success(
                                "MFA_DISABLED",
                                "MFA disabled successfully. Please log in again."));
    }

    // ==========================================
    // Helpers
    // ==========================================

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private UUID sessionId(Jwt jwt) {
        return UUID.fromString(
                jwt.getClaimAsString("sid"));
    }

    private MfaChallengeResponse toResponse(
            MfaChallenge challenge) {
        return new MfaChallengeResponse(
                challenge.getId(),
                challenge.getMethod().name(),
                mfaOtpService.getOtpExpiresInSeconds());
    }
}