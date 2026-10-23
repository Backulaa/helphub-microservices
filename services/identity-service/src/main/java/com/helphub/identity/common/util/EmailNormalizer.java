package com.helphub.identity.common.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException(
                    "Email must not be null");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}