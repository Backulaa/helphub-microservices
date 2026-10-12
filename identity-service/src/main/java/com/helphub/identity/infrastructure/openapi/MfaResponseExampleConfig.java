
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

import static com.helphub.identity.infrastructure.openapi.MfaResponseExamples.*;

import static com.helphub.identity.infrastructure.openapi.AuthResponseExamples.UNAUTHORIZED;
import static com.helphub.identity.infrastructure.openapi.AuthResponseExamples.VALIDATION_ERROR;
import static com.helphub.identity.infrastructure.openapi.AuthResponseExamples.CSRF_FORBIDDEN;

@Configuration
public class MfaResponseExampleConfig {

    private static final String MFA = "/api/v1/auth/mfa";

    private static final PathItem.HttpMethod GET = PathItem.HttpMethod.GET;

    private static final PathItem.HttpMethod POST = PathItem.HttpMethod.POST;

    @Bean
    public OpenApiCustomizer mfaResponseExampleCustomizer(
            JsonMapper jsonMapper) {
        return openApi -> {

            // ======================================
            // Get MFA Status
            // ======================================

            add(openApi, GET, MFA + "/status",
                    "200", MFA_STATUS_FETCHED, jsonMapper);

            add(openApi, GET, MFA + "/status",
                    "401", UNAUTHORIZED, jsonMapper);

            // ======================================
            // Start MFA Setup
            // ======================================

            add(openApi, POST, MFA + "/setup/start",
                    "200", MFA_SETUP_STARTED, jsonMapper);

            add(openApi, POST, MFA + "/setup/start",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, MFA + "/setup/start",
                    "401", UNAUTHORIZED, jsonMapper);

            add(openApi, POST, MFA + "/setup/start",
                    "429", MFA_RATE_LIMITED, jsonMapper);

            // ======================================
            // Confirm MFA Setup
            // ======================================

            add(openApi, POST, MFA + "/setup/confirm",
                    "200", MFA_ENABLED, jsonMapper);

            add(openApi, POST, MFA + "/setup/confirm",
                    "400", INVALID_MFA_CHALLENGE_CONFIRM, jsonMapper);

            add(openApi, POST, MFA + "/setup/confirm",
                    "401", UNAUTHORIZED, jsonMapper);

            // ======================================
            // Verify MFA Login
            // ======================================

            add(openApi, POST, MFA + "/login/verify",
                    "200", MFA_LOGIN_SUCCESS, jsonMapper);

            add(openApi, POST, MFA + "/login/verify",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, MFA + "/login/verify",
                    "401", INVALID_MFA_CHALLENGE_LOGIN, jsonMapper);

            add(openApi, POST, MFA + "/login/verify",
                    "403", CSRF_FORBIDDEN, jsonMapper);

            // ======================================
            // Start MFA Disable
            // ======================================

            add(openApi, POST, MFA + "/disable/start",
                    "200", MFA_DISABLE_STARTED, jsonMapper);

            add(openApi, POST, MFA + "/disable/start",
                    "400", VALIDATION_ERROR, jsonMapper);

            add(openApi, POST, MFA + "/disable/start",
                    "401", UNAUTHORIZED, jsonMapper);

            add(openApi, POST, MFA + "/disable/start",
                    "429", MFA_RATE_LIMITED, jsonMapper);

            // ======================================
            // Confirm MFA Disable
            // ======================================

            add(openApi, POST, MFA + "/disable/confirm",
                    "200", MFA_DISABLED, jsonMapper);

            add(openApi, POST, MFA + "/disable/confirm",
                    "400", INVALID_MFA_CHALLENGE_CONFIRM, jsonMapper);

            add(openApi, POST, MFA + "/disable/confirm",
                    "401", UNAUTHORIZED, jsonMapper);
        };
    }

    // ==========================================
    // Attach JSON Example to API Response
    // ==========================================

    private static void add(
            OpenAPI openApi,
            PathItem.HttpMethod method,
            String path,
            String status,
            String json,
            JsonMapper jsonMapper) {

        if (openApi.getPaths() == null) {
            throw new IllegalStateException(
                    "OpenAPI document has no paths");
        }

        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null) {
            throw new IllegalStateException(
                    "OpenAPI path not found: " + path);
        }

        Operation operation = pathItem.readOperationsMap().get(method);

        if (operation == null || operation.getResponses() == null) {
            throw new IllegalStateException(
                    "OpenAPI operation not found: "
                            + method + " " + path);
        }

        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            throw new IllegalStateException(
                    "OpenAPI response not found: "
                            + method + " " + path
                            + " [" + status + "]");
        }

        Content content = response.getContent();

        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        // Preserve the existing response schema
        // when converting */* to application/json.
        MediaType mediaType = content.get("application/json");

        if (mediaType == null) {
            mediaType = content.get("*/*");
        }

        if (mediaType == null) {
            mediaType = new MediaType();
        }

        content.addMediaType("application/json", mediaType);
        content.remove("*/*");

        // Parse JSON into a structured example object.
        Object example = jsonMapper.readValue(
                json,
                Object.class);

        mediaType.setExample(example);
    }
}
