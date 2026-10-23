
package com.helphub.identity.infrastructure.openapi;

public final class MfaResponseExamples {

    private MfaResponseExamples() {
    }

    // ==========================================
    // GET MFA Status - 200
    // ==========================================

    public static final String MFA_STATUS_FETCHED = """
            {
              "success": true,
              "code": "MFA_STATUS_FETCHED",
              "message": "MFA status retrieved successfully",
              "data": {
                "mfaEnabled": true,
                "mfaMethod": "EMAIL_OTP",
                "mfaEnabledAt": "2026-10-12T07:00:00Z"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Start MFA Setup - 200
    // ==========================================

    public static final String MFA_SETUP_STARTED = """
            {
              "success": true,
              "code": "MFA_SETUP_STARTED",
              "message": "Verification code sent to your email",
              "data": {
                "challengeId": "550e8400-e29b-41d4-a716-446655440001",
                "method": "EMAIL_OTP",
                "expiresIn": 300
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Confirm MFA Setup - 200
    // ==========================================

    public static final String MFA_ENABLED = """
            {
              "success": true,
              "code": "MFA_ENABLED",
              "message": "MFA enabled successfully. Please log in again.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Verify MFA Login - 200
    // ==========================================

    public static final String MFA_LOGIN_SUCCESS = """
            {
              "success": true,
              "code": "MFA_LOGIN_SUCCESS",
              "message": "MFA authentication completed successfully",
              "data": {
                "tokenType": "Bearer",
                "accessToken": "eyJhbGciOiJIUzI1NiJ9.example.signature",
                "expiresIn": 900,
                "sessionId": "550e8400-e29b-41d4-a716-446655440000"
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Start MFA Disable - 200
    // ==========================================

    public static final String MFA_DISABLE_STARTED = """
            {
              "success": true,
              "code": "MFA_DISABLE_STARTED",
              "message": "Verification code sent to your email",
              "data": {
                "challengeId": "550e8400-e29b-41d4-a716-446655440002",
                "method": "EMAIL_OTP",
                "expiresIn": 300
              },
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Confirm MFA Disable - 200
    // ==========================================

    public static final String MFA_DISABLED = """
            {
              "success": true,
              "code": "MFA_DISABLED",
              "message": "MFA disabled successfully. Please log in again.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Confirm MFA Setup / Disable - 400
    // ==========================================

    public static final String INVALID_MFA_CHALLENGE_CONFIRM = """
            {
              "success": false,
              "code": "INVALID_MFA_CHALLENGE",
              "message": "MFA verification failed",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // Verify MFA Login - 401
    // ==========================================

    public static final String INVALID_MFA_CHALLENGE_LOGIN = """
            {
              "success": false,
              "code": "INVALID_MFA_CHALLENGE",
              "message": "Invalid, expired or already used MFA challenge",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // OTP Rate Limit - 429
    // ==========================================

    public static final String MFA_RATE_LIMITED = """
            {
              "success": false,
              "code": "MFA_RATE_LIMITED",
              "message": "Too many MFA verification requests. Please try again later.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;

    // ==========================================
    // OTP Cooldown - 429
    // ==========================================

    public static final String MFA_COOLDOWN = """
            {
              "success": false,
              "code": "MFA_COOLDOWN",
              "message": "Please wait before requesting another verification code.",
              "data": null,
              "timestamp": "2026-10-12T08:00:00Z"
            }
            """;
}
