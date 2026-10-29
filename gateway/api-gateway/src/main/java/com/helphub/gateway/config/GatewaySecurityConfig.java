
package com.helphub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.util.UUID;

@Configuration
public class GatewaySecurityConfig {

        @Bean
        public SecurityWebFilterChain securityWebFilterChain(
                        ServerHttpSecurity http) {

                return http
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                                .authorizeExchange(auth -> auth
                                                .pathMatchers(HttpMethod.OPTIONS, "/**")
                                                .permitAll()

                                                .pathMatchers(HttpMethod.GET,
                                                                "/actuator/health",
                                                                "/actuator/health/**",
                                                                "/api/v1/auth/csrf")
                                                .permitAll()

                                                .pathMatchers(HttpMethod.POST,
                                                                "/api/v1/auth/register",
                                                                "/api/v1/auth/email/resend",
                                                                "/api/v1/auth/email/verify",
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/mfa/login/verify",
                                                                "/api/v1/auth/password/forgot",
                                                                "/api/v1/auth/password/reset")
                                                .permitAll()

                                                .pathMatchers(
                                                                "/api/v1/notifications",
                                                                "/api/v1/notifications/**")
                                                .authenticated()

                                                .anyExchange().authenticated())

                                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))

                                .build();
        }

        @Bean
        public ReactiveJwtDecoder jwtDecoder(
                        @Value("${security.jwt.jwk-set-uri}") String jwksUri,
                        @Value("${security.jwt.issuer}") String issuer,
                        @Value("${security.jwt.audience}") String audience) {

                NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                                .withJwkSetUri(jwksUri)
                                .jwsAlgorithm(SignatureAlgorithm.RS256)
                                .build();

                OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);

                decoder.setJwtValidator(jwt -> {
                        OAuth2TokenValidatorResult result = defaults.validate(jwt);

                        if (result.hasErrors()) {
                                return result;
                        }

                        try {
                                boolean valid = jwt.getAudience().contains(audience)
                                                && "access".equals(
                                                                jwt.getClaimAsString("token_type"))
                                                && jwt.getClaim("sv") instanceof Number
                                                && jwt.getClaimAsString("role") != null;

                                UUID.fromString(jwt.getSubject());
                                UUID.fromString(jwt.getClaimAsString("sid"));

                                if (valid) {
                                        return OAuth2TokenValidatorResult.success();
                                }
                        } catch (RuntimeException ignored) {
                                // Reject invalid required claims.
                        }

                        return OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error(
                                                        "invalid_token",
                                                        "Required JWT claims are invalid",
                                                        null));
                });

                return decoder;
        }
}
