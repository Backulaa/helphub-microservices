package com.helphub.identity.auth.service;

import com.helphub.identity.session.entity.LoginSession;
import com.helphub.identity.user.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final long accessTokenMinutes;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.access-token-minutes:15}") long accessTokenMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String generateAccessToken(
            User user,
            LoginSession session) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(
                        now.plus(Duration.ofMinutes(accessTokenMinutes)))
                .claim("sid", session.getId().toString())
                .claim("role", user.getRole().getCode())
                .claim("token_type", "access")
                .claim("sv", user.getSecurityVersion())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public long getAccessTokenExpiresIn() {
        return Duration.ofMinutes(accessTokenMinutes)
                .toSeconds();
    }
}