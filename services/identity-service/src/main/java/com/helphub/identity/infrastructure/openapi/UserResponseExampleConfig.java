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

import java.util.Map;

import static com.helphub.identity.infrastructure.openapi.UserResponseExamples.*;

@Configuration
public class UserResponseExampleConfig {

    private static final String USER = "/api/v1/users";
    private static final String ADMIN = "/api/v1/admin/users";

    private static final PathItem.HttpMethod GET = PathItem.HttpMethod.GET;

    private static final PathItem.HttpMethod PATCH = PathItem.HttpMethod.PATCH;

    @Bean
    public OpenApiCustomizer userResponseExampleCustomizer(
            JsonMapper jsonMapper) {

        return openApi -> {

            // ======================================
            // GET /users/me
            // ======================================

            addExamples(openApi, GET, USER + "/me",
                    Map.of(
                            "200", MY_PROFILE_FETCHED,
                            "401", UNAUTHORIZED),
                    jsonMapper);

            // ======================================
            // PATCH /users/me
            // ======================================

            addExamples(openApi, PATCH, USER + "/me",
                    Map.of(
                            "200", MY_PROFILE_UPDATED,
                            "400", INVALID_PROFILE_FIELD,
                            "401", UNAUTHORIZED,
                            "409", CONCURRENT_MODIFICATION),
                    jsonMapper);

            // ======================================
            // GET /users/{id}/public
            // ======================================

            addExamples(openApi, GET, USER + "/{id}/public",
                    Map.of(
                            "200", PUBLIC_PROFILE_FETCHED,
                            "400", INVALID_PARAMETER,
                            "401", UNAUTHORIZED,
                            "404", USER_NOT_FOUND,
                            "429", RATE_LIMITED),
                    jsonMapper);

            // ======================================
            // GET /admin/users/{id}
            // ======================================

            addExamples(openApi, GET, ADMIN + "/{id}",
                    Map.of(
                            "200", ADMIN_USER_FETCHED,
                            "400", INVALID_PARAMETER,
                            "401", UNAUTHORIZED,
                            "403", ACCESS_DENIED,
                            "404", USER_NOT_FOUND),
                    jsonMapper);

            // ======================================
            // GET /admin/users
            // ======================================

            addExamples(openApi, GET, ADMIN,
                    Map.of(
                            "200", ADMIN_USERS_FETCHED,
                            "400", VALIDATION_ERROR,
                            "401", UNAUTHORIZED,
                            "403", ACCESS_DENIED),
                    jsonMapper);

            // ======================================
            // PATCH /admin/users/{id}/role
            // ======================================

            addExamples(openApi, PATCH, ADMIN + "/{id}/role",
                    Map.of(
                            "200", USER_ROLE_CHANGED,
                            "400", VALIDATION_ERROR,
                            "401", UNAUTHORIZED,
                            "403", ACCESS_DENIED,
                            "404", USER_NOT_FOUND,
                            "409", ROLE_TRANSITION_NOT_ALLOWED),
                    jsonMapper);

            // ======================================
            // PATCH /admin/users/{id}/status
            // ======================================

            addExamples(openApi, PATCH, ADMIN + "/{id}/status",
                    Map.of(
                            "200", USER_STATUS_CHANGED,
                            "400", VALIDATION_ERROR,
                            "401", UNAUTHORIZED,
                            "403", ACCESS_DENIED,
                            "404", USER_NOT_FOUND,
                            "409", STATUS_TRANSITION_NOT_ALLOWED),
                    jsonMapper);
        };
    }

    // ==========================================
    // Attach Examples
    // ==========================================

    private static void addExamples(
            OpenAPI openApi,
            PathItem.HttpMethod method,
            String path,
            Map<String, String> examples,
            JsonMapper jsonMapper) {

        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null) {
            throw new IllegalStateException(
                    "OpenAPI path not found: " + path);
        }

        Operation operation = pathItem.readOperationsMap().get(method);

        if (operation == null
                || operation.getResponses() == null) {

            throw new IllegalStateException(
                    "OpenAPI operation not found: "
                            + method + " " + path);
        }

        for (Map.Entry<String, String> entry : examples.entrySet()) {

            String status = entry.getKey();
            String json = entry.getValue();

            ApiResponse response = operation.getResponses().get(status);

            if (response == null) {
                throw new IllegalStateException(
                        "OpenAPI response not found: "
                                + path + " [" + status + "]");
            }

            Content content = response.getContent();

            if (content == null) {
                content = new Content();
                response.setContent(content);
            }

            MediaType mediaType = content.get("application/json");

            if (mediaType == null) {
                mediaType = new MediaType();
                content.addMediaType(
                        "application/json",
                        mediaType);
            }

            Object example = jsonMapper.readValue(json, Object.class);

            mediaType.setExample(example);
        }
    }
}