
package com.helphub.gateway.filter;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        // Generate a trusted request ID at the gateway.
        String requestId = UUID.randomUUID().toString();

        // Forward the request ID to downstream services.
        // Remove client-supplied identity headers.
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set(REQUEST_ID_HEADER, requestId);
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                })
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .build();

        // Ensure the response includes the ID,
        // including responses rejected by Spring Security.
        mutatedExchange.getResponse()
                .getHeaders()
                .set(REQUEST_ID_HEADER, requestId);

        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse()
                    .getHeaders()
                    .set(REQUEST_ID_HEADER, requestId);

            return Mono.empty();
        });

        return chain.filter(mutatedExchange);
    }
}
