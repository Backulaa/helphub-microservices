package com.helphub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DevSwaggerRoutesConfig {

    @Bean
    public RouteLocator swaggerRoutes(
            RouteLocatorBuilder builder,

            @Value("${IDENTITY_SERVICE_URL:http://localhost:8081}") String identityUrl,

            @Value("${MEDIA_SERVICE_URL:http://localhost:8082}") String mediaUrl,

            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8083}") String notificationUrl) {

        return builder.routes()

                // ==================================
                // Identity OpenAPI
                // Actual path: /v1/api-docs
                // ==================================
                .route("swagger-identity", r -> r
                        .path("/_docs/identity")
                        .filters(f -> f.setPath("/v1/api-docs"))
                        .uri(identityUrl))

                // ==================================
                // Media OpenAPI
                // ==================================
                .route("swagger-media", r -> r
                        .path("/_docs/media")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(mediaUrl))

                // ==================================
                // Notification OpenAPI
                // ==================================
                .route("swagger-notification", r -> r
                        .path("/_docs/notification")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(notificationUrl))

                .build();
    }
}
