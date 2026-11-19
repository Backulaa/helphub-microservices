package com.helphub.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end HTTP regression tests for a RUNNING HELPhub stack.
 *
 * Run explicitly with -Dtest=GatewaySecurityCorsIT. The *IT suffix keeps this
 * class out of the default Maven Surefire unit-test suite.
 *
 * Required environment variables: HELPHUB_TEST_EMAIL, HELPHUB_TEST_PASSWORD.
 * Optional: HELPHUB_GATEWAY_URL, HELPHUB_MEDIA_URL, HELPHUB_FRONTEND_ORIGIN.
 * Use a dedicated, verified, non-MFA DEV account; never use production credentials.
 */
class GatewaySecurityCorsIT {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static String gateway;
    private static String media;
    private static String origin;
    private static String accessToken;
    private static String missingMediaPath;

    @BeforeAll
    static void prepare() throws Exception {
        gateway = baseUrl("HELPHUB_GATEWAY_URL", "http://localhost:8080");
        media = baseUrl("HELPHUB_MEDIA_URL", "http://localhost:8082");
        origin = envOrDefault("HELPHUB_FRONTEND_ORIGIN", "http://localhost:5173");
        missingMediaPath = "/api/v1/media/" + UUID.randomUUID();

        String email = requiredEnv("HELPHUB_TEST_EMAIL");
        String password = requiredEnv("HELPHUB_TEST_PASSWORD");

        // GET CSRF first so the HttpClient cookie jar receives the CSRF cookie.
        HttpResponse<String> csrfResponse = send(
                "GET", gateway + "/api/v1/auth/csrf", Map.of(), null);
        assertEquals(200, csrfResponse.statusCode(), "Cannot fetch CSRF token");
        String csrfToken = jsonString(csrfResponse.body(), "token");

        String loginJson = "{\"email\":\"" + jsonEscape(email)
                + "\",\"password\":\"" + jsonEscape(password) + "\"}";

        HttpResponse<String> login = send("POST", gateway + "/api/v1/auth/login",
                Map.of("Content-Type", "application/json", "X-XSRF-TOKEN", csrfToken),
                loginJson);
        assertEquals(200, login.statusCode(),
                "Login failed; check credentials, CSRF and whether MFA is enabled");
        accessToken = jsonString(login.body(), "accessToken");
        assertFalse(accessToken.isBlank(), "No accessToken returned by login");
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String baseUrl(String name, String fallback) {
        return envOrDefault(name, fallback).replaceAll("/+$", "");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " must be set for HTTP integration tests");
        assertFalse(value.isBlank(), name + " must not be blank");
        return value;
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /** Extracts simple string fields from these specific, known API responses. */
    private static String jsonString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), "Missing JSON string field: " + field);
        return matcher.group(1);
    }

    private static HttpResponse<String> send(String method, String url,
                                             Map<String, String> headers,
                                             String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15));
        headers.forEach(builder::header);
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return HTTP.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return send("GET", url, Map.of(), null);
    }

    private static Map<String, String> bearer() {
        return Map.of("Authorization", "Bearer " + accessToken);
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private static void assertCorsAllowed(HttpResponse<?> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(origin, header(response, "Access-Control-Allow-Origin"));
        assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
    }

    // ===== 11 JWT / Gateway / Media HTTP security checks =====

    @Test
    void security01_gatewayHealthIsPublic() throws Exception {
        assertEquals(200, get(gateway + "/actuator/health").statusCode());
    }

    @Test
    void security02_mediaHealthIsPublic() throws Exception {
        assertEquals(200, get(media + "/actuator/health").statusCode());
    }

    @Test
    void security03_getMediaWithoutJwtReturns401() throws Exception {
        assertEquals(401, get(gateway + missingMediaPath).statusCode());
    }

    @Test
    void security04_invalidJwtReturns401() throws Exception {
        assertEquals(401, send("GET", gateway + missingMediaPath,
                Map.of("Authorization", "Bearer invalid-token"), null).statusCode());
    }

    @Test
    void security05_uploadSessionWithoutJwtReturns401() throws Exception {
        assertEquals(401, send("POST", gateway + "/api/v1/media/upload-sessions",
                Map.of(), null).statusCode());
    }

    @Test
    void security06_deleteMediaWithoutJwtReturns401() throws Exception {
        assertEquals(401, send("DELETE", gateway + missingMediaPath,
                Map.of(), null).statusCode());
    }

    @Test
    void security07_internalMediaWithoutKeyReturns401() throws Exception {
        assertEquals(401, send("POST", media + "/internal/media/references",
                Map.of(), null).statusCode());
    }

    @Test
    void security08_internalMediaWithWrongKeyReturns401() throws Exception {
        assertEquals(401, send("POST", media + "/internal/media/references",
                Map.of("X-Internal-Service", "community",
                        "X-Internal-Key", "invalid-key"), null).statusCode());
    }

    @Test
    void security09_validJwtCanAccessAuthMe() throws Exception {
        assertEquals(200, send("GET", gateway + "/api/v1/auth/me",
                bearer(), null).statusCode());
    }

    @Test
    void security10_validJwtGets404ForUnknownMedia() throws Exception {
        assertEquals(404, send("GET", gateway + missingMediaPath,
                bearer(), null).statusCode());
    }

    @Test
    void security11_internalMediaRouteIsNotExposedViaGateway() throws Exception {
        assertEquals(404, send("POST", gateway + "/internal/media/references",
                bearer(), null).statusCode());
    }

    // ===== 4 CORS HTTP checks =====

    @Test
    void cors01_getPreflightFromAllowedOrigin() throws Exception {
        HttpResponse<String> response = send("OPTIONS", gateway + missingMediaPath,
                Map.of("Origin", origin,
                        "Access-Control-Request-Method", "GET",
                        "Access-Control-Request-Headers", "authorization"), null);
        assertCorsAllowed(response, 200);
    }

    @Test
    void cors02_postPreflightFromAllowedOrigin() throws Exception {
        HttpResponse<String> response = send("OPTIONS",
                gateway + "/api/v1/media/upload-sessions",
                Map.of("Origin", origin,
                        "Access-Control-Request-Method", "POST",
                        "Access-Control-Request-Headers", "authorization,content-type"), null);
        assertCorsAllowed(response, 200);
    }

    @Test
    void cors03_preflightFromUntrustedOriginIsRejected() throws Exception {
        HttpResponse<String> response = send("OPTIONS", gateway + missingMediaPath,
                Map.of("Origin", "https://untrusted.example",
                        "Access-Control-Request-Method", "GET"), null);
        assertEquals(403, response.statusCode());
        assertTrue(header(response, "Access-Control-Allow-Origin").isBlank(),
                "Untrusted origin must not receive CORS permission");
    }

    @Test
    void cors04_authenticatedGetHasCorsHeaders() throws Exception {
        HttpResponse<String> response = send("GET", gateway + missingMediaPath,
                Map.of("Origin", origin,
                        "Authorization", "Bearer " + accessToken), null);
        assertCorsAllowed(response, 404);
    }

    // ===== 4 Security Headers / JWT forwarding HTTP checks =====

    @Test
    void headers01_forgedIdentityWithoutJwtStillReturns401() throws Exception {
        HttpResponse<String> response = send("GET", gateway + missingMediaPath,
                Map.of("X-User-Id", UUID.randomUUID().toString(),
                       "X-User-Role", "ADMIN"), null);
        assertEquals(401, response.statusCode(),
                "Client-supplied identity headers must not bypass JWT authentication");
    }

    @Test
    void headers02_forgedIdentityDoesNotOverrideValidJwt() throws Exception {
        HttpResponse<String> response = send("GET", gateway + missingMediaPath,
                Map.of("Authorization", "Bearer " + accessToken,
                       "X-User-Id", UUID.randomUUID().toString(),
                       "X-User-Role", "ADMIN"), null);
        assertEquals(404, response.statusCode(),
                "A valid JWT should be accepted despite forged client identity headers");
    }

    @Test
    void headers03_gatewayReplacesClientSuppliedRequestId() throws Exception {
        String clientRequestId = "client-forged-request-id";
        HttpResponse<String> response = send("GET", gateway + "/actuator/health",
                Map.of("X-Request-ID", clientRequestId), null);

        assertEquals(200, response.statusCode());
        String actualRequestId = header(response, "X-Request-ID");
        assertFalse(actualRequestId.isBlank(), "Missing X-Request-ID in response");
        assertEquals(UUID.fromString(actualRequestId).toString(), actualRequestId,
                "Gateway must generate a canonical UUID request ID");
        assertNotEquals(clientRequestId, actualRequestId,
                "Gateway must not trust a client-supplied request ID");
    }

    @Test
    void headers04_eachRequestGetsUniqueRequestId() throws Exception {
        HttpResponse<String> first = get(gateway + "/actuator/health");
        HttpResponse<String> second = get(gateway + "/actuator/health");

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());

        String firstId = header(first, "X-Request-ID");
        String secondId = header(second, "X-Request-ID");
        assertFalse(firstId.isBlank(), "First response missing X-Request-ID");
        assertFalse(secondId.isBlank(), "Second response missing X-Request-ID");
        assertEquals(UUID.fromString(firstId).toString(), firstId);
        assertEquals(UUID.fromString(secondId).toString(), secondId);
        assertNotEquals(firstId, secondId,
                "Gateway must generate a new request ID for every request");
    }
}
