package com.helphub.identity.user.controller;

import com.helphub.identity.common.dto.ApiResponse;

import com.helphub.identity.user.dto.MyProfileResponse;
import com.helphub.identity.user.dto.PublicUserProfileResponse;
import com.helphub.identity.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "User Management", description = "User profile management APIs")
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ==========================================
    // Get My Profile
    // ==========================================

    @Operation(summary = "Get my profile", description = "Retrieve the authenticated user's profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        MyProfileResponse result = userService.getMyProfile(jwt);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "USER_PROFILE_FETCHED",
                        "User profile retrieved successfully",
                        result));
    }

    // ==========================================
    // Update My Profile
    // ==========================================

    @Operation(summary = "Update my profile", description = """
            Partially update the authenticated user's profile.

            Allowed fields:
            - fullName
            - phoneNumber
            - dateOfBirth
            - gender

            Missing fields remain unchanged.
            Nullable fields can be cleared with null.
            Protected fields are rejected.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid profile update"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrent update conflict")
    })
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {

        MyProfileResponse result = userService.updateMyProfile(jwt, request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "USER_PROFILE_UPDATED",
                        "User profile updated successfully",
                        result));
    }

    // ==========================================
    // Get Public User Profile
    // ==========================================

    @Operation(summary = "Get public user profile", description = """
            Retrieve a public profile by user ID.

            The caller must be authenticated.
            The target account must be ACTIVE
            and email verified.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Public profile retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid user ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Public profile not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping("/{id}/public")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getPublicUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        PublicUserProfileResponse result = userService.getPublicUserProfile(jwt, id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "PUBLIC_USER_PROFILE_FETCHED",
                        "Public user profile retrieved successfully",
                        result));
    }
}