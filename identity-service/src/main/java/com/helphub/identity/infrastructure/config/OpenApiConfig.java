package com.helphub.identity.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", description = "Paste your JWT Access Token")
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI identityServiceOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("HelpHub - IdentityService API")
                                .description(
                                        """
                                                Identity and Access Management Service
                                                for the HelpHub Microservices System.

                                                Main features:
                                                - User Registration
                                                - Email Verification
                                                - Authentication
                                                - JWT & Refresh Token
                                                - Multi-Factor Authentication
                                                - User & Role Management
                                                """)
                                .version("v1.0.0")
                                .contact(
                                        new Contact()
                                                .name("HelpHub Development Team")));
    }
}