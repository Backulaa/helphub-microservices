package com.helphub.media.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration
public class InternalMediaSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain internalMediaFilterChain(
            HttpSecurity http,

            @Value("${app.media.internal.community-key:}") String communityKey,

            @Value("${app.media.internal.support-key:}") String supportKey) throws Exception {

        OncePerRequestFilter serviceAuthFilter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain)
                    throws ServletException, IOException {

                String serviceName = request.getHeader("X-Internal-Service");

                String suppliedKey = request.getHeader("X-Internal-Key");

                String expectedKey = switch (serviceName == null ? "" : serviceName) {
                    case "community" -> communityKey;
                    case "support" -> supportKey;
                    default -> "";
                };

                boolean valid = expectedKey != null
                        && expectedKey.length() >= 32
                        && suppliedKey != null
                        && constantTimeEquals(
                                expectedKey,
                                suppliedKey);

                if (!valid) {
                    response.setStatus(401);
                    response.setContentType(
                            MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"success\":false,"
                                    + "\"code\":\"INVALID_SERVICE_AUTH\","
                                    + "\"message\":\"Unauthorized internal caller\","
                                    + "\"data\":null}");
                    return;
                }

                SecurityContext context = SecurityContextHolder.createEmptyContext();

                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                serviceName,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_MEDIA_INTERNAL"))));

                SecurityContextHolder.setContext(context);

                chain.doFilter(request, response);
            }
        };

        http
                .securityMatcher("/internal/media/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .hasRole("MEDIA_INTERNAL"))
                .addFilterBefore(
                        serviceAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static boolean constantTimeEquals(
            String expected,
            String supplied) {

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
