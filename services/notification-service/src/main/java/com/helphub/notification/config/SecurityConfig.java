package com.helphub.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

        @Value("${app.security.jwt.jwk-set-uri}")
        private String jwkSetUri;

        @Value("${app.security.jwt.issuer}")
        private String issuer;

        @Value("${app.security.jwt.audience}")
        private String audience;

        // ==========================================
        // 1. Configure HTTP security
        // ==========================================
        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http) throws Exception {

                http
                                // Stateless REST API using Bearer tokens.
                                .csrf(AbstractHttpConfigurer::disable)

                                // CORS will be managed by API Gateway.
                                .cors(AbstractHttpConfigurer::disable)

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // Allow Spring Boot to render error responses.
                                                .dispatcherTypeMatchers(
                                                                DispatcherType.ERROR)
                                                .permitAll()

                                                // Public health endpoints.
                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/actuator/info")
                                                .permitAll()

                                                // Protected Notification API.
                                                .requestMatchers(
                                                                "/api/v1/notifications",
                                                                "/api/v1/notifications/**")
                                                .authenticated()

                                                // Deny other HTTP endpoints.
                                                .anyRequest().denyAll())

                                // Verify JWT Bearer token.
                                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

                return http.build();
        }

        // ==========================================
        // 2. Configure JWT decoder and validation
        // ==========================================
        @Bean
        public JwtDecoder jwtDecoder() {

                NimbusJwtDecoder decoder = NimbusJwtDecoder
                                .withJwkSetUri(jwkSetUri)
                                .jwsAlgorithm(SignatureAlgorithm.RS256)
                                .build();

                // Validate issuer, expiry and not-before.
                OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuer);

                // Validate audience.
                OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                aud -> aud != null
                                                && aud.contains(audience));

                // Ensure subject contains a valid user UUID.
                OAuth2TokenValidator<Jwt> subjectValidator = new JwtClaimValidator<String>(
                                JwtClaimNames.SUB,
                                SecurityConfig::isValidUuid);

                // Apply all validators.
                decoder.setJwtValidator(
                                new DelegatingOAuth2TokenValidator<>(
                                                defaultValidator,
                                                audienceValidator,
                                                subjectValidator));

                return decoder;
        }

        private static boolean isValidUuid(String value) {

                if (value == null || value.isBlank()) {
                        return false;
                }

                try {
                        UUID.fromString(value);
                        return true;
                } catch (IllegalArgumentException exception) {
                        return false;
                }
        }
}
