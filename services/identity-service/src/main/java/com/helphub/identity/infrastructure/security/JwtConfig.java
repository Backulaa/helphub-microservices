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
import org.springframework.security.oauth2.jwt.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nimbusds.jose.jwk.RSAKey;

@Configuration
public class JwtConfig {
    @Bean
    public JwtDecoder jwtDecoder(
            RSAKey signingJwk,
            UserRepository userRepository,
            LoginSessionRepository sessionRepository,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder;

        try {
            decoder = NimbusJwtDecoder
                    .withPublicKey(signingJwk.toRSAPublicKey())
                    .build();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException(
                    "Cannot load JWT verification key", ex);
        }

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