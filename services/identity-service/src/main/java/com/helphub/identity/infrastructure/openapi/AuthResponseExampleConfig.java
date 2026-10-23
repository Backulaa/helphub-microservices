
package com.helphub.identity.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import static com.helphub.identity.infrastructure.openapi.AuthResponseExamples.*;

@Configuration
public class AuthResponseExampleConfig {

    private static final String AUTH = "/api/v1/auth";

    private static final PathItem.HttpMethod GET = PathItem.HttpMethod.GET;

    private static final PathItem.HttpMethod POST = PathItem.HttpMethod.POST;

    @Bean
    public OpenApiCustomizer authResponseExampleCustomizer(
            JsonMapper jsonMapper) {
        return openApi -> {

            // ======================================
            // Register
            // ======================================

            add(openApi, POST, AUTH + "/register",
                    "201", REGISTER_CREATED, jsonMapper);

            add(openApi, POST, AUTH + "/register",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, AUTH + "/register",
                    "409", EMAIL_ALREADY_REGISTERED, jsonMapper);

            add(openApi, POST, AUTH + "/register",
                    "429", RATE_LIMITED, jsonMapper);

            // ======================================
            // Resend Email OTP
            // ======================================

            add(openApi, POST, AUTH + "/email/resend",
                    "202", RESEND_ACCEPTED, jsonMapper);

            add(openApi, POST, AUTH + "/email/resend",
                    "400", VALIDATION_ERROR, jsonMapper);

            // ======================================
            // Verify Email OTP
            // ======================================

            add(openApi, POST, AUTH + "/email/verify",
                    "200", EMAIL_VERIFIED, jsonMapper);

            add(openApi, POST, AUTH + "/email/verify",
                    "400", INVALID_EMAIL_VERIFICATION, jsonMapper);

            // ======================================
            // Login
            // ======================================

            add(openApi, POST, AUTH + "/login",
                    "200", LOGIN_SUCCESS, jsonMapper);

            add(openApi, POST, AUTH + "/login",
                    "202", MFA_REQUIRED, jsonMapper);

            add(openApi, POST, AUTH + "/login",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, AUTH + "/login",
                    "401", INVALID_CREDENTIALS, jsonMapper);

            add(openApi, POST, AUTH + "/login",
                    "403", CSRF_FORBIDDEN, jsonMapper);

            add(openApi, POST, AUTH + "/login",
                    "429", RATE_LIMITED, jsonMapper);

            // ======================================
            // Refresh Token
            // ======================================

            add(openApi, POST, AUTH + "/refresh",
                    "200", TOKEN_REFRESHED, jsonMapper);

            add(openApi, POST, AUTH + "/refresh",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, AUTH + "/refresh",
                    "401", INVALID_REFRESH_TOKEN, jsonMapper);

            add(openApi, POST, AUTH + "/refresh",
                    "403", CSRF_FORBIDDEN, jsonMapper);

            // ======================================
            // Logout
            // ======================================

            add(openApi, POST, AUTH + "/logout",
                    "200", LOGOUT_SUCCESS, jsonMapper);

            add(openApi, POST, AUTH + "/logout",
                    "401", UNAUTHORIZED, jsonMapper);

            // ======================================
            // Current User
            // ======================================

            add(openApi, GET, AUTH + "/me",
                    "200", CURRENT_USER_FETCHED, jsonMapper);

            add(openApi, GET, AUTH + "/me",
                    "401", UNAUTHORIZED, jsonMapper);

            // ======================================
            // CSRF Token
            // ======================================

            add(openApi, GET, AUTH + "/csrf",
                    "200", CSRF_TOKEN_ISSUED, jsonMapper);

            // ======================================
            // Forgot Password
            // ======================================

            add(openApi, POST, AUTH + "/password/forgot",
                    "202", PASSWORD_RESET_REQUEST_ACCEPTED, jsonMapper);

            add(openApi, POST, AUTH + "/password/forgot",
                    "400", VALIDATION_ERROR, jsonMapper);

            // ======================================
            // Reset Password
            // ======================================

            add(openApi, POST, AUTH + "/password/reset",
                    "200", PASSWORD_RESET_SUCCESS, jsonMapper);

            add(openApi, POST, AUTH + "/password/reset",
                    "400", PASSWORD_RESET_FAILED, jsonMapper);

            // ======================================
            // Change Password
            // ======================================

            add(openApi, POST, AUTH + "/password/change",
                    "200", PASSWORD_CHANGED, jsonMapper);

            add(openApi, POST, AUTH + "/password/change",
                    "400", INVALID_CURRENT_PASSWORD, jsonMapper);

            add(openApi, POST, AUTH + "/password/change",
                    "401", UNAUTHORIZED, jsonMapper);

            add(openApi, POST, AUTH + "/password/change",
                    "403", ACCOUNT_NOT_ELIGIBLE, jsonMapper);
        };
    }

    // ==========================================
    // Attach JSON example to existing API response
    // ==========================================

    private static void add(
            OpenAPI openApi,
            PathItem.HttpMethod method,
            String path,
            String status,
            String json,
            JsonMapper jsonMapper) {

        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null) {
            throw new IllegalStateException(
                    "OpenAPI path not found: " + path);
        }

        Operation operation = pathItem.readOperationsMap().get(method);

        if (operation == null || operation.getResponses() == null) {
            throw new IllegalStateException(
                    "OpenAPI operation not found: " + method + " " + path);
        }

        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            throw new IllegalStateException(
                    "OpenAPI response not found: "
                            + method + " " + path + " [" + status + "]");
        }

        Content content = response.getContent();

        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType mediaType = content.get("application/json");

        if (mediaType == null) {
            mediaType = new MediaType();
            content.addMediaType("application/json", mediaType);
        }

        // Convert JSON string into an actual JSON object,
        // instead of displaying it as a quoted string.
        Object example = jsonMapper.readValue(json, Object.class);

        // Preserve any existing response schema.
        mediaType.setExample(example);
    }
}
