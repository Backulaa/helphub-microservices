package com.helphub.identity.infrastructure.security;

import com.helphub.identity.common.dto.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }

        // ==========================================
        // JWT Role Mapping
        // ==========================================

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {

                JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

                authoritiesConverter.setAuthoritiesClaimName("role");
                authoritiesConverter.setAuthorityPrefix("ROLE_");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(
                                authoritiesConverter);

                return converter;
        }

        // ==========================================
        // CSRF Token Repository
        // ==========================================

        @Bean
        public CookieCsrfTokenRepository csrfTokenRepository(
                        @Value("${app.security.cookie.secure:false}") boolean secure) {
                CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();

                repository.setCookiePath("/");

                repository.setCookieCustomizer(cookie -> cookie
                                .httpOnly(true)
                                .secure(secure)
                                .sameSite("Strict")
                                .path("/"));

                return repository;
        }

        // ==========================================
        // Security Filter Chain
        // ==========================================

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JsonMapper jsonMapper,
                        JwtAuthenticationConverter jwtAuthenticationConverter,
                        CookieCsrfTokenRepository csrfTokenRepository) throws Exception {

                http

                                // Disable CORS for now, as it is handled by the API Gateway.
                                .cors(AbstractHttpConfigurer::disable)

                                // CSRF is required for cookie-based refresh.
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(csrfTokenRepository)

                                                .requireCsrfProtectionMatcher(request -> {

                                                        if (!"POST".equals(request.getMethod())) {
                                                                return false;
                                                        }

                                                        String path = request.getServletPath();

                                                        return "/api/v1/auth/login".equals(path)
                                                                        || "/api/v1/auth/refresh".equals(path)
                                                                        || "/api/v1/auth/mfa/login/verify".equals(path);
                                                }))

                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                // ==================================
                                // Security Error Responses
                                // ==================================

                                .exceptionHandling(ex -> ex

                                                .authenticationEntryPoint(
                                                                (request, response, exception) -> writeSecurityError(
                                                                                response,
                                                                                jsonMapper,
                                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "UNAUTHORIZED",
                                                                                "Authentication is required or token is invalid"))

                                                .accessDeniedHandler(
                                                                (request, response, exception) -> writeSecurityError(
                                                                                response,
                                                                                jsonMapper,
                                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                                "ACCESS_DENIED",
                                                                                "Access denied or CSRF token is invalid")))

                                // ==================================
                                // Authorization
                                // ==================================

                                .authorizeHttpRequests(auth -> auth

                                                // Swagger UI and OpenAPI
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v1/api-docs",
                                                                "/v1/api-docs/**",
                                                                "/v1/api-docs.yaml")
                                                .permitAll()

                                                // Public authentication endpoints
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/v1/auth/register",
                                                                "/api/v1/auth/email/resend",
                                                                "/api/v1/auth/email/verify",
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/mfa/login/verify",
                                                                "/api/v1/auth/password/forgot",
                                                                "/api/v1/auth/password/reset")
                                                .permitAll()

                                                // Obtain a CSRF token
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/v1/auth/csrf")
                                                .permitAll()

                                                // Health check
                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()

                                                // Change Password requires authentication
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/v1/auth/password/change")
                                                .authenticated()

                                                // Admin-only APIs
                                                .requestMatchers(
                                                                "/api/v1/admin/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers(HttpMethod.GET, "/internal/jwks")
                                                .permitAll()

                                                .anyRequest().authenticated())

                                // ==================================
                                // JWT Resource Server
                                // ==================================

                                .oauth2ResourceServer(oauth2 -> oauth2

                                                .authenticationEntryPoint(
                                                                (request, response, exception) -> writeSecurityError(
                                                                                response,
                                                                                jsonMapper,
                                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "UNAUTHORIZED",
                                                                                "Authentication is required or token is invalid"))

                                                .accessDeniedHandler(
                                                                (request, response, exception) -> writeSecurityError(
                                                                                response,
                                                                                jsonMapper,
                                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                                "ACCESS_DENIED",
                                                                                "You do not have permission to access this resource"))

                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter)));

                return http.build();
        }

        // ==========================================
        // Unified Security Error Response
        // ==========================================

        private static void writeSecurityError(
                        HttpServletResponse response,
                        JsonMapper jsonMapper,
                        int status,
                        String code,
                        String message) throws IOException {

                response.setStatus(status);

                response.setContentType(
                                MediaType.APPLICATION_JSON_VALUE);

                response.setCharacterEncoding(
                                StandardCharsets.UTF_8.name());

                ApiResponse<Void> body = ApiResponse.error(code, message);

                jsonMapper.writeValue(
                                response.getOutputStream(),
                                body);
        }
}