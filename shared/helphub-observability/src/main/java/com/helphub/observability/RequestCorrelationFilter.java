package com.helphub.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String incomingId = request.getHeader(REQUEST_ID_HEADER);

        String requestId = isValid(incomingId)
                ? incomingId
                : UUID.randomUUID().toString();

        // Return the effective ID to the HTTP client.
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // Preserve any existing MDC value.
        String previousId = MDC.get(MDC_KEY);

        MDC.put(MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Restore MDC to prevent cross-request leakage.
            if (previousId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousId);
            }
        }
    }

    private boolean isValid(String value) {
        return value != null
                && VALID_REQUEST_ID.matcher(value).matches();
    }
}
