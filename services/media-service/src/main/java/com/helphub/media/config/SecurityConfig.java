package com.helphub.media.config;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {

        @Value("${app.security.jwt.jwk-set-uri}")
        private String jwkSetUri;

        @Value("${app.security.jwt.issuer}")
        private String issuer;

        @Value("${app.security.jwt.audience}")
        private String audience;

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        @Value("${app.swagger.enabled:false}") boolean swaggerEnabled) throws Exception {

                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> {

                                        auth.dispatcherTypeMatchers(
                                                        DispatcherType.ERROR).permitAll();

                                        auth.requestMatchers(
                                                        "/actuator/health",
                                                        "/actuator/info").permitAll();

                                        // DEV OpenAPI
                                        if (swaggerEnabled) {
                                                auth.requestMatchers(
                                                                org.springframework.http.HttpMethod.GET,
                                                                "/v3/api-docs",
                                                                "/v3/api-docs/**").permitAll();
                                        }

                                        // Create Upload Session
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.POST,
                                                        "/api/v1/media/upload-sessions").authenticated();

                                        // Complete PUBLIC Upload
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.POST,
                                                        "/api/v1/media/*/complete").authenticated();

                                        // Upload PRIVATE file through Backend
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.POST,
                                                        "/api/v1/media/*/upload").authenticated();

                                        // Read metadata by ID.
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.GET,
                                                        "/api/v1/media/*").authenticated();

                                        // Soft delete owned media
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.DELETE,
                                                        "/api/v1/media/*").authenticated();

                                        // Retrieve multiple metadata records.
                                        auth.requestMatchers(
                                                        org.springframework.http.HttpMethod.POST,
                                                        "/api/v1/media/batch").authenticated();

                                        // Other APIs are not enabled yet
                                        auth.anyRequest().denyAll();
                                })

                                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

                return http.build();
        }

        @Bean
        public JwtDecoder jwtDecoder() {

                NimbusJwtDecoder decoder = NimbusJwtDecoder
                                .withJwkSetUri(jwkSetUri)
                                .jwsAlgorithm(SignatureAlgorithm.RS256)
                                .build();

                OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);

                OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                audiences -> audiences != null
                                                && audiences.contains(audience));

                OAuth2TokenValidator<Jwt> subjectValidator = new JwtClaimValidator<String>(
                                JwtClaimNames.SUB,
                                SecurityConfig::isValidUuid);

                OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtClaimValidator<String>(
                                "token_type",
                                "access"::equals);

                decoder.setJwtValidator(
                                new DelegatingOAuth2TokenValidator<>(
                                                defaults,
                                                audienceValidator,
                                                subjectValidator,
                                                tokenTypeValidator));

                return decoder;
        }

        private static boolean isValidUuid(String value) {
                try {
                        UUID.fromString(value);
                        return true;
                } catch (IllegalArgumentException | NullPointerException ex) {
                        return false;
                }
        }
}
