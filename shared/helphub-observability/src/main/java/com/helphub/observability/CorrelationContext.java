package com.helphub.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationContext {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private CorrelationContext() {
    }

    // Get the current HTTP request ID,
    // or create a new one for non-HTTP operations.
    public static String currentOrNew() {
        String current = MDC.get(MDC_KEY);

        return isValid(current)
                ? current
                : UUID.randomUUID().toString();
    }

    // Read a request ID from an external message header.
    public static String fromHeaderOrNew(Object headerValue) {
        if (headerValue instanceof String value && isValid(value)) {
            return value;
        }

        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        return value != null
                && VALID_ID.matcher(value).matches();
    }

    // Activate MDC for the current thread.
    public static Scope open(String requestId) {
        String previousId = MDC.get(MDC_KEY);

        MDC.put(MDC_KEY, fromHeaderOrNew(requestId));

        return () -> {
            if (previousId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousId);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
