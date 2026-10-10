package com.helphub.identity.infrastructure.security;

import com.helphub.identity.session.entity.AuthenticationLevel;
import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.session.repository.LoginSessionRepository;
import com.helphub.identity.user.entity.AccountStatus;
import com.helphub.identity.user.entity.User;
import com.helphub.identity.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${security.jwt.secret-base64}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);

        if (decoded.length < 32) {
            throw new IllegalArgumentException(
                    "JWT key must contain at least 32 bytes");
        }

        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            UserRepository userRepository,
            LoginSessionRepository sessionRepository,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);

        decoder.setJwtValidator(jwt -> {

            // Standard checks: issuer, timestamps, etc.
            OAuth2TokenValidatorResult baseResult = defaults.validate(jwt);

            if (baseResult.hasErrors()) {
                return baseResult;
            }

            try {
                if (!jwt.getAudience().contains(audience)) {
                    return invalid();
                }

                if (!"access".equals(
                        jwt.getClaimAsString("token_type"))) {
                    return invalid();
                }

                UUID userId = UUID.fromString(jwt.getSubject());

                UUID sessionId = UUID.fromString(
                        jwt.getClaimAsString("sid"));

                Object versionClaim = jwt.getClaim("sv");

                if (!(versionClaim instanceof Number number)) {
                    return invalid();
                }

                long securityVersion = number.longValue();

                User user = userRepository
                        .findById(userId)
                        .orElse(null);

                LoginSession session = sessionRepository
                        .findById(sessionId)
                        .orElse(null);

                if (user == null || session == null) {
                    return invalid();
                }

                if (!session.getUser().getId().equals(userId)) {
                    return invalid();
                }

                if (user.getStatus() != AccountStatus.ACTIVE
                        || !user.isEmailVerified()) {
                    return invalid();
                }

                if (session.getRevokedAt() != null) {
                    return invalid();
                }

                if (!session.getExpiresAt().isAfter(
                        OffsetDateTime.now(ZoneOffset.UTC))) {
                    return invalid();
                }

                if (user.getSecurityVersion() != securityVersion
                        || session.getSecurityVersion() != securityVersion) {
                    return invalid();
                }

                // A password-only session cannot bypass MFA.
                if (user.isMfaEnabled()
                        && session.getAuthenticationLevel() != AuthenticationLevel.MFA) {
                    return invalid();
                }

                return OAuth2TokenValidatorResult.success();

            } catch (RuntimeException ex) {
                return invalid();
            }
        });

        return decoder;
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        "Access token is invalid or session is inactive",
                        null));
    }
}