package com.helphub.identity.auth.controller;

import com.helphub.identity.auth.dto.RegisterRequest;
import com.helphub.identity.auth.dto.RegisterResponse;
import com.helphub.identity.auth.dto.ResendEmailOtpRequest;
import com.helphub.identity.auth.dto.ResetPasswordRequest;
import com.helphub.identity.auth.dto.VerifyEmailOtpRequest;
import com.helphub.identity.auth.dto.LoginRequest;
import com.helphub.identity.auth.dto.TokenResponse;
import com.helphub.identity.auth.dto.ChangePasswordRequest;
import com.helphub.identity.auth.dto.CsrfTokenResponse;
import com.helphub.identity.auth.dto.CurrentUserResponse;
import com.helphub.identity.auth.dto.ForgotPasswordRequest;
import com.helphub.identity.auth.service.SessionAuthService;
import com.helphub.identity.auth.service.IssuedTokens;
import com.helphub.identity.auth.service.LoginResult;
import com.helphub.identity.auth.service.AuthService;

import com.helphub.identity.infrastructure.security.RefreshCookieService;

import com.helphub.identity.common.dto.ApiResponse;
import com.helphub.identity.common.exception.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Authentication", description = "Registration and email verification APIs")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

        private final AuthService authService;
        private final SessionAuthService sessionAuthService;
        private final RefreshCookieService refreshCookieService;

        public AuthController(
                        AuthService authService,
                        SessionAuthService sessionAuthService,
                        RefreshCookieService refreshCookieService) {
                this.authService = authService;
                this.sessionAuthService = sessionAuthService;
                this.refreshCookieService = refreshCookieService;
        }

        // ==========================================
        // Register
        // ==========================================

        @Operation(summary = "Register a new account", description = """
                        Register a new HelpHub account.

                        Allowed roles: REQUESTER and VOLUNTEER.
                        Email verification is required before login.
                        """)
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account registered successfully")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid registration data")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
        @PostMapping("/register")
        public ResponseEntity<ApiResponse<RegisterResponse>> register(
                        @Valid @RequestBody RegisterRequest request) {

                RegisterResponse result = authService.register(request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                ApiResponse.success(
                                                                "USER_REGISTERED",
                                                                "Registration successful. Please verify your email.",
                                                                result));
        }

        // ==========================================
        // Resend Email OTP
        // ==========================================

        @Operation(summary = "Resend email verification OTP", description = """
                        Request a verification OTP for an eligible account.

                        The response does not disclose
                        whether the email exists.
                        """)
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Verification request accepted")
        @PostMapping("/email/resend")
        public ResponseEntity<ApiResponse<Void>> resendEmailOtp(
                        @Valid @RequestBody ResendEmailOtpRequest request) {

                authService.resendEmailOtp(request.email());

                return ResponseEntity
                                .accepted()
                                .body(
                                                ApiResponse.success(
                                                                "EMAIL_VERIFICATION_REQUEST_ACCEPTED",
                                                                "If the email belongs to an eligible account, " +
                                                                                "a verification email will be requested."));
        }

        // ==========================================
        // Verify Email OTP
        // ==========================================

        @Operation(summary = "Verify email OTP", description = """
                        Verify the 6-digit OTP sent to the user's email.

                        A valid OTP can only be used once.
                        """)
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
        @PostMapping("/email/verify")
        public ResponseEntity<ApiResponse<Void>> verifyEmailOtp(
                        @Valid @RequestBody VerifyEmailOtpRequest request) {

                boolean verified = authService.verifyEmailOtp(
                                request.email(),
                                request.otp());

                if (!verified) {
                        throw new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "INVALID_EMAIL_VERIFICATION",
                                        "Verification failed or code is no longer valid");
                }

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                "EMAIL_VERIFIED",
                                                "Email verified successfully. You can now log in."));
        }

        // ==========================================
        // Login
        // ==========================================

        @Operation(summary = "Login with email and password", description = """
                        If MFA is disabled, returns Access Token
                        and sets Refresh Token cookie.

                        If MFA is enabled, returns HTTP 202 with
                        a challengeId and does not issue tokens.
                        """)
        @PostMapping("/login")
        public ResponseEntity<?> login(
                        @Valid @RequestBody LoginRequest request,

                        @RequestHeader("X-XSRF-TOKEN") String csrfToken) {
                LoginResult result = sessionAuthService.login(request);

                // MFA is required.
                if (result instanceof LoginResult.RequiresMfa pending) {

                        return ResponseEntity
                                        .status(HttpStatus.ACCEPTED)
                                        .header(
                                                        HttpHeaders.SET_COOKIE,
                                                        refreshCookieService.clear())
                                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                        .body(
                                                        ApiResponse.success(
                                                                        "MFA_REQUIRED",
                                                                        "A verification code has been sent to your email",
                                                                        pending.challenge()));
                }

                // Password-only authentication completed.
                LoginResult.Authenticated authenticated = (LoginResult.Authenticated) result;

                IssuedTokens issued = authenticated.tokens();

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
                                                                "LOGIN_SUCCESS",
                                                                "Login successful",
                                                                response));
        }

        // ==========================================
        // Refresh Token
        // ==========================================

        @Operation(summary = "Rotate Refresh Token using HttpOnly cookie")
        @PostMapping("/refresh")
        public ResponseEntity<ApiResponse<TokenResponse>> refresh(
                        @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String rawRefreshToken,

                        @RequestHeader("X-XSRF-TOKEN") String csrfTokenHeader) {
                IssuedTokens issued = rawRefreshToken == null || rawRefreshToken.isBlank()
                                ? null
                                : sessionAuthService.refresh(rawRefreshToken);

                if (issued == null) {
                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .header(
                                                        HttpHeaders.SET_COOKIE,
                                                        refreshCookieService.clear())
                                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                        .body(
                                                        ApiResponse.error(
                                                                        "INVALID_REFRESH_TOKEN",
                                                                        "Refresh token is invalid, expired or revoked",
                                                                        (TokenResponse) null));
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
                                                                "TOKEN_REFRESHED",
                                                                "Tokens refreshed successfully",
                                                                response));
        }

        // ==========================================
        // Logout
        // ==========================================

        @Operation(summary = "Logout current session")
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
        @PostMapping("/logout")
        public ResponseEntity<ApiResponse<Void>> logout(
                        @AuthenticationPrincipal Jwt jwt) {
                UUID userId = UUID.fromString(jwt.getSubject());

                UUID sessionId = UUID.fromString(
                                jwt.getClaimAsString("sid"));

                sessionAuthService.logout(userId, sessionId);

                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.SET_COOKIE,
                                                refreshCookieService.clear())
                                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                .body(
                                                ApiResponse.success(
                                                                "LOGOUT_SUCCESS",
                                                                "Logged out successfully"));
        }

        // ==========================================
        // Get Current User
        // ==========================================

        @Operation(summary = "Get current authenticated user")
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
        @GetMapping("/me")
        public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
                        @AuthenticationPrincipal Jwt jwt) {
                UUID userId = UUID.fromString(jwt.getSubject());

                CurrentUserResponse user = sessionAuthService.getCurrentUser(userId);

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                "CURRENT_USER_FETCHED",
                                                "Current user retrieved successfully",
                                                user));
        }

        // ==========================================
        // Get CSRF Token
        // ==========================================

        @Operation(summary = "Get CSRF token for cookie-based refresh")
        @GetMapping("/csrf")
        public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(
                        CsrfToken csrfToken) {
                CsrfTokenResponse response = new CsrfTokenResponse(
                                csrfToken.getHeaderName(),
                                csrfToken.getToken());

                return ResponseEntity.ok()
                                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                .body(
                                                ApiResponse.success(
                                                                "CSRF_TOKEN_ISSUED",
                                                                "CSRF token issued successfully",
                                                                response));
        }

        // ==========================================
        // Forgot Password
        // ==========================================

        @Operation(summary = "Request password reset", description = """
                        Request a password reset OTP.

                        Always returns a generic response
                        to prevent account enumeration.
                        """)
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Password reset request accepted")
        @PostMapping("/password/forgot")
        public ResponseEntity<ApiResponse<Void>> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {

                authService.forgotPassword(request.email());

                return ResponseEntity.accepted()
                                .header(
                                                HttpHeaders.CACHE_CONTROL,
                                                "no-store")
                                .body(
                                                ApiResponse.success(
                                                                "PASSWORD_RESET_REQUEST_ACCEPTED",
                                                                "If the email belongs to an eligible account, " +
                                                                                "password reset instructions will be requested."));
        }

        // ==========================================
        // Reset Password
        // ==========================================

        @Operation(summary = "Reset account password", description = """
                        Reset password using a valid email OTP.

                        On success, all existing login sessions
                        and refresh tokens are revoked.

                        MFA settings remain unchanged.
                        """)
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request, password or OTP")
        @PostMapping("/password/reset")
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {

                boolean resetSuccessful = authService.resetPassword(request);

                if (!resetSuccessful) {
                        return ResponseEntity.badRequest()
                                        .header(
                                                        HttpHeaders.CACHE_CONTROL,
                                                        "no-store")
                                        .body(
                                                        ApiResponse.error(
                                                                        "PASSWORD_RESET_FAILED",
                                                                        "Password reset request is invalid or expired"));
                }

                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.SET_COOKIE,
                                                refreshCookieService.clear())
                                .header(
                                                HttpHeaders.CACHE_CONTROL,
                                                "no-store")
                                .body(
                                                ApiResponse.success(
                                                                "PASSWORD_RESET_SUCCESS",
                                                                "Password reset successfully. Please log in again."));
        }

        // ==========================================
        // Change Password
        // ==========================================

        @Operation(summary = "Change account password", description = """
                        Change the password of the authenticated user.

                        Requires the current password.

                        On success:
                        - Updates the password hash.
                        - Revokes all existing login sessions.
                        - Revokes all refresh tokens.
                        - Invalidates previously issued access tokens.
                        - Preserves MFA configuration.
                        - Requires the user to log in again.
                        """)
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
        @PostMapping("/password/change")
        public ResponseEntity<ApiResponse<Void>> changePassword(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody ChangePasswordRequest request) {

                // Get user ID from authenticated JWT.
                UUID userId = UUID.fromString(
                                jwt.getSubject());

                // Perform change password operation.
                authService.changePassword(
                                userId,
                                request);

                // Clear refresh token cookie in current browser.
                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.SET_COOKIE,
                                                refreshCookieService.clear())
                                .header(
                                                HttpHeaders.CACHE_CONTROL,
                                                "no-store")
                                .body(
                                                ApiResponse.success(
                                                                "PASSWORD_CHANGED",
                                                                "Password changed successfully. Please log in again."));
        }
}