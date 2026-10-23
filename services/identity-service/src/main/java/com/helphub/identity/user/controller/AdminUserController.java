package com.helphub.identity.user.controller;

import com.helphub.identity.common.dto.ApiResponse;

import com.helphub.identity.user.dto.*;
import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.Gender;
import com.helphub.identity.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin User Management", description = "Administrative user management APIs")
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    // ==========================================
    // Get User By ID
    // ==========================================

    @Operation(summary = "Get user details", description = "Retrieve user details by UUID. ADMIN only.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid user ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin permission required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        AdminUserResponse result = userService.getUserById(jwt, id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "ADMIN_USER_FETCHED",
                        "User retrieved successfully",
                        result));
    }

    // ==========================================
    // Search Users
    // ==========================================

    @Operation(summary = "Search users", description = """
            Search, filter, sort, and paginate users.

            Available filters:
            - keyword
            - roleCode
            - status
            - emailVerified
            - gender
            - ageGroup

            Default page: 0
            Default size: 20
            Maximum size: 50

            Allowed sort:
            - createdAt,asc
            - createdAt,desc
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid search parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin permission required")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<UserPageResponse>> searchUsers(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam(required = false) String keyword,

            @RequestParam(required = false) String roleCode,

            @RequestParam(required = false) AccountStatus status,

            @RequestParam(required = false) Boolean emailVerified,

            @RequestParam(required = false) Gender gender,

            @RequestParam(required = false) AgeGroup ageGroup,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size,

            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        UserPageResponse result = userService.searchUsers(
                jwt,
                keyword,
                roleCode,
                status,
                emailVerified,
                gender,
                ageGroup,
                page,
                size,
                sort);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "ADMIN_USERS_FETCHED",
                        "Users retrieved successfully",
                        result));
    }

    // ==========================================
    // Change User Role
    // ==========================================

    @Operation(summary = "Change user role", description = """
            Change another user's role.

            Requires current ADMIN permission.
            Validates the role transition matrix.
            Revokes all existing sessions and tokens.

            Self-modification is prohibited.
            The last eligible ADMIN is protected.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role changed or no-op"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden operation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule or concurrency conflict")
    })
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeUserRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserRoleRequest request) {

        AdminUserResponse result = userService.changeUserRole(jwt, id, request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "USER_ROLE_CHANGED",
                        "User role operation completed successfully",
                        result));
    }

    // ==========================================
    // Change User Status
    // ==========================================

    @Operation(summary = "Change user status", description = """
            Suspend, disable, or reactivate a user.

            Requires current ADMIN permission.
            Validates status transitions.
            Revokes existing sessions and tokens.

            Self-modification is prohibited.
            The last eligible ADMIN is protected.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status changed or no-op"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden operation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule or concurrency conflict")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request) {

        AdminUserResponse result = userService.changeUserStatus(jwt, id, request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(
                        "USER_STATUS_CHANGED",
                        "User status operation completed successfully",
                        result));
    }
}