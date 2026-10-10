package com.helphub.identity.auth.service;

import com.helphub.identity.common.exception.BusinessException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {

    // Small local blocklist for initial development only.
    private static final Set<String> BLOCKED = Set.of(
            "password123456789",
            "123456789012345",
            "1234567890123456",
            "qwertyqwerty123",
            "passwordpassword");

    public void validate(String password) {

        if (password == null) {
            reject();
        }

        int length = password.codePointCount(
                0,
                password.length());

        if (length < 15 || length > 64) {
            reject();
        }

        // BCrypt has a 72-byte effective input limit.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            reject();
        }

        if (BLOCKED.contains(
                password.toLowerCase(Locale.ROOT))) {
            reject();
        }
    }

    private void reject() {
        throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PASSWORD",
                "Password does not meet security requirements");
    }
}