package com.helphub.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("dev")
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenAPI() {

        String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("HELPhub - Notification Service")
                        .description("Notification APIs")
                        .version("v1.0.0"))
                .servers(List.of(
                        new Server()
                                .url("/")
                                .description("API Gateway")))
                .components(new Components()
                        .addSecuritySchemes(
                                schemeName,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(schemeName));
    }
}
