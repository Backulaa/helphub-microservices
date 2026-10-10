package com.helphub.identity.infrastructure.security;

import com.helphub.identity.auth.service.IssuedTokens;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshCookieService {

    public static final String COOKIE_NAME = "hh_refresh";

    private static final String COOKIE_PATH = "/api/v1/auth/refresh";

    private final boolean secure;

    public RefreshCookieService(
            @Value("${app.security.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String create(IssuedTokens tokens) {

        Duration remaining = Duration.between(
                Instant.now(),
                tokens.sessionExpiresAt().toInstant());

        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalStateException(
                    "Cannot issue a cookie for an expired session");
        }

        return ResponseCookie
                .from(COOKIE_NAME, tokens.refreshToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(remaining)
                .build()
                .toString();
    }

    public String clear() {

        return ResponseCookie
                .from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }
}