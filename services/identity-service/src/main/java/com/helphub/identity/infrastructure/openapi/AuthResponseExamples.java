
package com.helphub.identity.infrastructure.openapi;

public final class AuthResponseExamples {

    private AuthResponseExamples() {
    }

    // ==========================================
    // Shared Error Responses
    // ==========================================

    public static final String VALIDATION_ERROR = """
            {
              "success": false,
              "code": "VALIDATION_ERROR",
              "message": "Request validation failed",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String UNAUTHORIZED = """
            {
              "success": false,
              "code": "UNAUTHORIZED",
              "message": "Authentication is required or token is invalid",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String RATE_LIMITED = """
            {
              "success": false,
              "code": "RATE_LIMITED",
              "message": "Too many requests. Please try again later.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String CSRF_FORBIDDEN = """
            {
              "success": false,
              "code": "INVALID_CSRF_TOKEN",
              "message": "CSRF token is missing or invalid",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Registration
    // ==========================================

    public static final String REGISTER_CREATED = """
            {
              "success": true,
              "code": "USER_REGISTERED",
              "message": "Registration successful. Please verify your email.",
              "data": {
                "userId": "550e8400-e29b-41d4-a716-446655440000",
                "email": "volunteer01@helphub.com",
                "emailVerified": false
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String EMAIL_ALREADY_REGISTERED = """
            {
              "success": false,
              "code": "EMAIL_ALREADY_REGISTERED",
              "message": "Email is already registered",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Email Verification
    // ==========================================

    public static final String RESEND_ACCEPTED = """
            {
              "success": true,
              "code": "EMAIL_VERIFICATION_REQUEST_ACCEPTED",
              "message": "If the email belongs to an eligible account, a verification email will be requested.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String EMAIL_VERIFIED = """
            {
              "success": true,
              "code": "EMAIL_VERIFIED",
              "message": "Email verified successfully. You can now log in.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String INVALID_EMAIL_VERIFICATION = """
            {
              "success": false,
              "code": "INVALID_EMAIL_VERIFICATION",
              "message": "Verification failed or code is no longer valid",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Login
    // ==========================================

    public static final String LOGIN_SUCCESS = """
            {
              "success": true,
              "code": "LOGIN_SUCCESS",
              "message": "Login successful",
              "data": {
                "tokenType": "Bearer",
                "accessToken": "eyJhbGciOiJIUzI1NiJ9.example.signature",
                "expiresIn": 900,
                "sessionId": "550e8400-e29b-41d4-a716-446655440000"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String MFA_REQUIRED = """
            {
              "success": true,
              "code": "MFA_REQUIRED",
              "message": "A verification code has been sent to your email",
              "data": {
                "challengeId": "550e8400-e29b-41d4-a716-446655440001",
                "method": "EMAIL_OTP",
                "expiresIn": 300
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String INVALID_CREDENTIALS = """
            {
              "success": false,
              "code": "INVALID_CREDENTIALS",
              "message": "Invalid email or password",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Refresh Token
    // ==========================================

    public static final String TOKEN_REFRESHED = """
            {
              "success": true,
              "code": "TOKEN_REFRESHED",
              "message": "Tokens refreshed successfully",
              "data": {
                "tokenType": "Bearer",
                "accessToken": "eyJhbGciOiJIUzI1NiJ9.refreshed.signature",
                "expiresIn": 900,
                "sessionId": "550e8400-e29b-41d4-a716-446655440000"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String INVALID_REFRESH_TOKEN = """
            {
              "success": false,
              "code": "INVALID_REFRESH_TOKEN",
              "message": "Refresh token is invalid, expired or revoked",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Logout
    // ==========================================

    public static final String LOGOUT_SUCCESS = """
            {
              "success": true,
              "code": "LOGOUT_SUCCESS",
              "message": "Logged out successfully",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Current User
    // ==========================================

    public static final String CURRENT_USER_FETCHED = """
            {
              "success": true,
              "code": "CURRENT_USER_FETCHED",
              "message": "Current user retrieved successfully",
              "data": {
                "userId": "550e8400-e29b-41d4-a716-446655440000",
                "email": "volunteer01@helphub.com",
                "fullName": "Nguyen Van A",
                "role": "VOLUNTEER"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // CSRF
    // ==========================================

    public static final String CSRF_TOKEN_ISSUED = """
            {
              "success": true,
              "code": "CSRF_TOKEN_ISSUED",
              "message": "CSRF token issued successfully",
              "data": {
                "headerName": "X-XSRF-TOKEN",
                "token": "example-masked-csrf-token"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Forgot Password
    // ==========================================

    public static final String PASSWORD_RESET_REQUEST_ACCEPTED = """
            {
              "success": true,
              "code": "PASSWORD_RESET_REQUEST_ACCEPTED",
              "message": "If the email belongs to an eligible account, password reset instructions will be requested.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Reset Password
    // ==========================================

    public static final String PASSWORD_RESET_SUCCESS = """
            {
              "success": true,
              "code": "PASSWORD_RESET_SUCCESS",
              "message": "Password reset successfully. Please log in again.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String PASSWORD_RESET_FAILED = """
            {
              "success": false,
              "code": "PASSWORD_RESET_FAILED",
              "message": "Password reset request is invalid or expired",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Change Password
    // ==========================================

    public static final String PASSWORD_CHANGED = """
            {
              "success": true,
              "code": "PASSWORD_CHANGED",
              "message": "Password changed successfully. Please log in again.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String INVALID_CURRENT_PASSWORD = """
            {
              "success": false,
              "code": "INVALID_CURRENT_PASSWORD",
              "message": "Current password is incorrect",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    public static final String ACCOUNT_NOT_ELIGIBLE = """
            {
              "success": false,
              "code": "ACCOUNT_NOT_ELIGIBLE",
              "message": "Account is not eligible to change password",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;
}
