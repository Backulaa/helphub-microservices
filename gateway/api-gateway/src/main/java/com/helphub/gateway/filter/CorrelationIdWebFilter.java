
package com.helphub.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;

import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String requestId = UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set("X-Request-ID", requestId);
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                })
                .build();

        exchange.getResponse()
                .getHeaders()
                .set("X-Request-ID", requestId);

        return chain.filter(
                exchange.mutate().request(request).build());
    }
}
