package com.helphub.media.media.access;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live DEV-only test of signed Cloudinary private-media access.
 *
 * Run explicitly with -Dtest=PrivateAccessGrantHttpIT; it is intentionally
 * excluded from the ordinary Surefire *Test suite.
 *
 * This test uploads a REAL asset to Cloudinary and schedules its deletion.
 * Use only a disposable DEV account and a nonproduction Cloudinary environment.
 * Never log an accessUrl, JWT, password or internal service key.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrivateAccessGrantHttpIT {

    private static final HttpClient API = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final HttpClient CLOUDINARY = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static String gateway;
    private static String mediaService;
    private static String accessToken;
    private static String communityKey;
    private static String ownerId;
    private static String mediaId;
    private static final String RESOURCE_ID = UUID.randomUUID().toString();
    private static boolean referenceAttached;

    @BeforeAll
    static void setUpRealAsset() throws Exception {
        assertEquals("true", required("HELPHUB_RUN_LIVE_GRANT_IT"),
                "Set HELPHUB_RUN_LIVE_GRANT_IT=true to acknowledge live Cloudinary uploads");

        gateway = baseUrl("HELPHUB_GATEWAY_URL", "http://host.docker.internal:8080");
        mediaService = baseUrl("HELPHUB_MEDIA_URL", "http://host.docker.internal:8082");
        // Do not allow accidentally running this state-changing test against prod.
        assertLocalDevUrl(gateway);
        assertLocalDevUrl(mediaService);

        communityKey = required("HELPHUB_COMMUNITY_KEY");
        assertTrue(communityKey.length() >= 32, "Community key must be at least 32 characters");

        HttpResponse<String> csrf = jsonRequest("GET", gateway + "/api/v1/auth/csrf",
                Map.of(), null);
        assertEquals(200, csrf.statusCode(), "Could not get CSRF token");
        String csrfToken = jsonString(csrf.body(), "token");

        String loginBody = "{\"email\":\"" + jsonEscape(required("HELPHUB_TEST_EMAIL"))
                + "\",\"password\":\"" + jsonEscape(required("HELPHUB_TEST_PASSWORD")) + "\"}";
        HttpResponse<String> login = jsonRequest("POST", gateway + "/api/v1/auth/login",
                Map.of("Content-Type", "application/json", "X-XSRF-TOKEN", csrfToken), loginBody);
        assertEquals(200, login.statusCode(), "DEV account login failed (or MFA is enabled)");
        accessToken = jsonString(login.body(), "accessToken");

        String[] jwtParts = accessToken.split("\\.");
        assertEquals(3, jwtParts.length, "Expected JWT access token");
        String payload = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
        ownerId = UUID.fromString(jsonString(payload, "sub")).toString();

        byte[] imageBytes = makeValidPng();
        String filename = "grant-it-" + UUID.randomUUID() + ".png";
        String sessionBody = "{\"filename\":\"" + filename
                + "\",\"mimeType\":\"image/png\",\"sizeBytes\":" + imageBytes.length
                + ",\"visibility\":\"PRIVATE\"}";

        HttpResponse<String> session = jsonRequest("POST",
                gateway + "/api/v1/media/upload-sessions", bearerJson(), sessionBody);
        assertEquals(201, session.statusCode(), "Could not create PRIVATE upload session");
        mediaId = UUID.fromString(jsonString(session.body(), "mediaId")).toString();

        HttpResponse<String> upload = uploadPng(gateway + "/api/v1/media/" + mediaId + "/upload",
                filename, imageBytes);
        assertEquals(200, upload.statusCode(), "PRIVATE image did not upload successfully");
        assertEquals("READY", jsonString(upload.body(), "status"),
                "Media is not READY after upload");

        String binding = "{\"mediaId\":\"" + mediaId + "\",\"ownerId\":\"" + ownerId
                + "\",\"resourceType\":\"POST\",\"resourceId\":\"" + RESOURCE_ID + "\"}";
        HttpResponse<String> attach = jsonRequest("POST", mediaService + "/internal/media/references",
                internalHeaders(), binding);
        assertEquals(200, attach.statusCode(), "Could not attach reference");
        referenceAttached = true;
    }

    @Test
    @Order(1)
    void grant01_missingInternalKeyIsUnauthorized() throws Exception {
        HttpResponse<String> response = jsonRequest("POST",
                mediaService + "/internal/media/access-grants",
                Map.of("Content-Type", "application/json"), grantBody(RESOURCE_ID));
        assertEquals(401, response.statusCode());
    }

    @Test
    @Order(2)
    void grant02_wrongResourceBindingIsNotFound() throws Exception {
        HttpResponse<String> response = jsonRequest("POST",
                mediaService + "/internal/media/access-grants",
                internalHeaders(), grantBody(UUID.randomUUID().toString()));
        assertEquals(404, response.statusCode());
        assertEquals("MEDIA_NOT_FOUND", jsonString(response.body(), "code"));
    }

    @Test
    @Order(3)
    void grant03_realSignedUrlWorksThenExpires() throws Exception {
        HttpResponse<String> grant = jsonRequest("POST",
                mediaService + "/internal/media/access-grants",
                internalHeaders(), grantBody(RESOURCE_ID));
        assertEquals(200, grant.statusCode(), "Could not issue a valid private grant");
        assertEquals("PRIVATE_ACCESS_GRANTED", jsonString(grant.body(), "code"));
        assertTrue(grant.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
                "Grant response must not be cached");

        String signedUrl = jsonString(grant.body(), "accessUrl").replace("\\/", "/");
        assertTrue(signedUrl.startsWith("https://"), "Grant URL must be HTTPS");
        Instant expiresAt = Instant.parse(jsonString(grant.body(), "expiresAt"));
        long ttl = jsonLong(grant.body(), "expiresInSeconds");
        assertTrue(ttl >= 15 && ttl <= 300, "Unexpected TTL");
        assertTrue(expiresAt.isAfter(Instant.now()), "Grant was already expired");

        // This checks ACTUAL Cloudinary delivery, unlike the Mockito unit tests.
        int initialStatus = cloudinaryStatus(signedUrl);
        assertEquals(200, initialStatus, "Signed URL must deliver the private image before expiry");

        // Wait only inside this explicit live integration test, never in unit tests.
        long waitMillis = Duration.between(Instant.now(), expiresAt.plusSeconds(5)).toMillis();
        assertTrue(waitMillis <= Duration.ofMinutes(6).toMillis(), "Unreasonable expiry time");
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }

        int expiredStatus = cloudinaryStatus(signedUrl);
        assertTrue(expiredStatus == 401 || expiredStatus == 403,
                "Expired private URL must be rejected by Cloudinary; HTTP " + expiredStatus);
    }

    @AfterAll
    static void deleteTestMedia() throws Exception {
        if (mediaId == null || accessToken == null) {
            return;
        }
        try {
            if (referenceAttached) {
                String detachBody = "{\"mediaId\":\"" + mediaId
                        + "\",\"resourceType\":\"POST\",\"resourceId\":\"" + RESOURCE_ID + "\"}";
                HttpResponse<String> detach = jsonRequest("DELETE",
                        mediaService + "/internal/media/references", internalHeaders(), detachBody);
                assertEquals(200, detach.statusCode(), "Failed to detach test reference");
            }
        } finally {
            // Soft delete: storage cleanup happens asynchronously (normally after 24 h).
            HttpResponse<String> delete = jsonRequest("DELETE",
                    gateway + "/api/v1/media/" + mediaId,
                    Map.of("Authorization", "Bearer " + accessToken), null);
            assertEquals(200, delete.statusCode(),
                    "Failed to schedule deletion of the test asset; check mediaId in DEV DB");
        }
    }

    private static String grantBody(String resourceId) {
        return "{\"mediaId\":\"" + mediaId + "\",\"viewerId\":\"" + ownerId
                + "\",\"resourceType\":\"POST\",\"resourceId\":\"" + resourceId + "\"}";
    }

    private static Map<String, String> internalHeaders() {
        return Map.of("Content-Type", "application/json",
                "X-Internal-Service", "community", "X-Internal-Key", communityKey);
    }

    private static Map<String, String> bearerJson() {
        return Map.of("Authorization", "Bearer " + accessToken,
                "Content-Type", "application/json");
    }

    private static HttpResponse<String> jsonRequest(String method, String url,
                                                     Map<String, String> headers, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return API.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> uploadPng(String url, String filename, byte[] png)
            throws Exception {
        String boundary = "----helphub-test-" + UUID.randomUUID();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(png);
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(50))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
                .build();
        return API.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static byte[] makeValidPng() throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                image.setRGB(x, y, ((x * 2) << 16) | ((y * 2) << 8) | 180);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output), "Cannot encode PNG");
        return output.toByteArray();
    }

    private static int cloudinaryStatus(String privateUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(privateUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .GET().build();
        return CLOUDINARY.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String jsonString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher match = pattern.matcher(json);
        assertTrue(match.find(), "Response is missing field: " + field);
        return match.group(1);
    }

    private static long jsonLong(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
        Matcher match = pattern.matcher(json);
        assertTrue(match.find(), "Response is missing numeric field: " + field);
        return Long.parseLong(match.group(1));
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertNotNull(value, "Environment variable missing: " + name);
        assertFalse(value.isBlank(), "Environment variable empty: " + name);
        return value;
    }

    private static String baseUrl(String name, String fallback) {
        return System.getenv(name) == null || System.getenv(name).isBlank()
                ? fallback : System.getenv(name).replaceAll("/+$", "");
    }

    private static void assertLocalDevUrl(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        assertTrue("http".equalsIgnoreCase(uri.getScheme())
                        && ("localhost".equals(host) || "127.0.0.1".equals(host)
                        || "host.docker.internal".equals(host)),
                "This live test may only target a local DEV stack");
    }
}
