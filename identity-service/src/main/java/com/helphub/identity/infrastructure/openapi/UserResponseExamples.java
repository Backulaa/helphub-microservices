package com.helphub.identity.infrastructure.openapi;

public final class UserResponseExamples {

    private UserResponseExamples() {
    }

    private static final String PROFILE_DATA = """
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "email": "volunteer@helphub.com",
              "fullName": "Nguyen Van A",
              "phoneNumber": "+84912345678",
              "dateOfBirth": "2002-05-10",
              "gender": "MALE",
              "avatarMediaId": null,
              "roleCode": "VOLUNTEER",
              "status": "ACTIVE",
              "emailVerified": true,
              "mfaEnabled": false,
              "mfaMethod": null,
              "createdAt": "2026-10-01T08:00:00Z",
              "updatedAt": "2026-10-10T08:00:00Z",
              "lastLoginAt": "2026-10-14T08:00:00Z"
            }
            """;

    private static final String PUBLIC_DATA = """
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "fullName": "Nguyen Van A",
              "email": "volunteer@helphub.com",
              "phoneNumber": "+84912345678",
              "dateOfBirth": "2002-05-10",
              "gender": "MALE",
              "avatarMediaId": null,
              "roleCode": "VOLUNTEER",
              "createdAt": "2026-10-01T08:00:00Z",
              "lastLoginAt": "2026-10-14T08:00:00Z"
            }
            """;

    private static final String ADMIN_DATA = """
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "email": "volunteer@helphub.com",
              "fullName": "Nguyen Van A",
              "phoneNumber": "+84912345678",
              "dateOfBirth": "2002-05-10",
              "gender": "MALE",
              "avatarMediaId": null,
              "roleCode": "VOLUNTEER",
              "status": "ACTIVE",
              "emailVerified": true,
              "mfaEnabled": false,
              "createdAt": "2026-10-01T08:00:00Z",
              "updatedAt": "2026-10-10T08:00:00Z",
              "lastLoginAt": "2026-10-14T08:00:00Z"
            }
            """;

    private static final String PAGE_DATA = """
            {
              "content": [
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "fullName": "Nguyen Van A",
                  "email": "volunteer@helphub.com",
                  "roleCode": "VOLUNTEER",
                  "status": "ACTIVE",
                  "emailVerified": true,
                  "createdAt": "2026-10-01T08:00:00Z"
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "sort": "createdAt,desc"
            }
            """;

    private static String success(
            String code,
            String message,
            String data) {

        return """
                {
                  "success": true,
                  "code": "%s",
                  "message": "%s",
                  "data": %s,
                  "timestamp": "2026-10-14T08:00:00Z"
                }
                """.formatted(code, message, data);
    }

    private static String error(
            String code,
            String message) {

        return """
                {
                  "success": false,
                  "code": "%s",
                  "message": "%s",
                  "data": null,
                  "timestamp": "2026-10-14T08:00:00Z"
                }
                """.formatted(code, message);
    }

    // ==========================================
    // Success Examples
    // ==========================================

    public static final String MY_PROFILE_FETCHED = success(
            "USER_PROFILE_FETCHED",
            "User profile retrieved successfully",
            PROFILE_DATA);

    public static final String MY_PROFILE_UPDATED = success(
            "USER_PROFILE_UPDATED",
            "User profile updated successfully",
            PROFILE_DATA);

    public static final String PUBLIC_PROFILE_FETCHED = success(
            "PUBLIC_USER_PROFILE_FETCHED",
            "Public user profile retrieved successfully",
            PUBLIC_DATA);

    public static final String ADMIN_USER_FETCHED = success(
            "ADMIN_USER_FETCHED",
            "User retrieved successfully",
            ADMIN_DATA);

    public static final String ADMIN_USERS_FETCHED = success(
            "ADMIN_USERS_FETCHED",
            "Users retrieved successfully",
            PAGE_DATA);

    public static final String USER_ROLE_CHANGED = success(
            "USER_ROLE_CHANGED",
            "User role operation completed successfully",
            ADMIN_DATA.replace(
                    "\"roleCode\": \"VOLUNTEER\"",
                    "\"roleCode\": \"COLLABORATOR\""));

    public static final String USER_STATUS_CHANGED = success(
            "USER_STATUS_CHANGED",
            "User status operation completed successfully",
            ADMIN_DATA.replace(
                    "\"status\": \"ACTIVE\"",
                    "\"status\": \"SUSPENDED\""));

    // ==========================================
    // Error Examples
    // ==========================================

    public static final String VALIDATION_ERROR = error(
            "VALIDATION_ERROR",
            "Request validation failed");

    public static final String UNAUTHORIZED = error(
            "UNAUTHORIZED",
            "Authentication is required or token is invalid");

    public static final String ACCESS_DENIED = error(
            "ACCESS_DENIED",
            "You do not have permission to access this resource");

    public static final String USER_NOT_FOUND = error(
            "USER_NOT_FOUND",
            "User not found");

    public static final String INVALID_PARAMETER = error(
            "INVALID_PARAMETER",
            "Invalid value for parameter: id");

    public static final String INVALID_PROFILE_FIELD = error(
            "INVALID_PROFILE_FIELD",
            "Field is not allowed: roleCode");

    public static final String CONCURRENT_MODIFICATION = error(
            "CONCURRENT_MODIFICATION",
            "Resource was modified concurrently. Please retry.");

    public static final String ROLE_TRANSITION_NOT_ALLOWED = error(
            "ROLE_TRANSITION_NOT_ALLOWED",
            "Role transition is not allowed");

    public static final String STATUS_TRANSITION_NOT_ALLOWED = error(
            "STATUS_TRANSITION_NOT_ALLOWED",
            "Disabled account cannot transition to suspended");

    public static final String RATE_LIMITED = error(
            "RATE_LIMITED",
            "Public profile request limit exceeded");
}