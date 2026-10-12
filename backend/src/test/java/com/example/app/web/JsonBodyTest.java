package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.app.testing.HttpsTestServer;
import com.example.app.testing.LogCapture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonBodyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VALID_BODY = "{\"email\":\"user@example.com\",\"password\":\"correct horse battery\"}";

    private static HttpsTestServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpsTestServer.start(app -> app.post("/api/echo", ctx -> {
            JsonBody body = JsonBody.read(ctx);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("email", body.string("email").orElse(null));
            result.put("password", body.string("password").orElse(null));
            result.put("hasAccountId", body.has("accountId"));
            ctx.contentType("application/json").result(MAPPER.writeValueAsString(result));
        }));
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.close();
    }

    private static JsonNode echo(String contentType, String body) throws Exception {
        HttpResponse<String> response = server.post("/api/echo", contentType, body);
        assertEquals(200, response.statusCode());
        return MAPPER.readTree(response.body());
    }

    private static void assertNoInput(JsonNode result) {
        assertTrue(result.get("email").isNull());
        assertTrue(result.get("password").isNull());
        assertFalse(result.get("hasAccountId").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/json; charset=utf-8", "Application/JSON"})
    void readsStringValuesOfJsonObject(String contentType) throws Exception {
        JsonNode result = echo(contentType, VALID_BODY);

        assertEquals("user@example.com", result.get("email").asText());
        assertEquals("correct horse battery", result.get("password").asText());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "application/jsonp", "text/json"})
    void treatsOtherContentTypesAsEmpty(String contentType) throws Exception {
        assertNoInput(echo(contentType, VALID_BODY));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{\"email\":",
            "{email: \"user@example.com\"}",
            "{\"email\":\"user@example.com\"} {}",
            "not json"})
    void treatsBrokenJsonAsEmpty(String body) throws Exception {
        assertNoInput(echo("application/json", body));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"email\":\"user@example.com\"}]",
            "\"user@example.com\"",
            "123",
            "null",
            "true"})
    void treatsNonObjectJsonAsEmpty(String body) throws Exception {
        assertNoInput(echo("application/json", body));
    }

    @Test
    void treatsNonStringValuesAsMissing() throws Exception {
        JsonNode result = echo("application/json",
                "{\"email\":123,\"password\":null,\"accountId\":{\"id\":1}}");

        assertTrue(result.get("email").isNull());
        assertTrue(result.get("password").isNull());
        // 値の種類によらず、項目があることは分かる
        assertTrue(result.get("hasAccountId").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{\"a\":1}", "false"})
    void treatsArrayAndBooleanValuesAsMissing(String value) throws Exception {
        JsonNode result = echo("application/json", "{\"email\":" + value + ",\"password\":\"p\"}");

        assertTrue(result.get("email").isNull());
        assertEquals("p", result.get("password").asText());
    }

    @Test
    void doesNotLogTheContentOfBrokenJson() throws Exception {
        String secret = "do-not-log-this-password";
        try (LogCapture logs = LogCapture.of(JsonBody.class)) {
            assertNoInput(echo("application/json", "{\"password\":\"" + secret + "\" oops"));

            assertFalse(logs.events().isEmpty());
            for (ILoggingEvent event : logs.events()) {
                assertFalse(event.getFormattedMessage().contains(secret));
                assertNull(event.getThrowableProxy());
            }
        }
    }

    @Test
    void acceptsBodyOfExactly64Kilobytes() throws Exception {
        String body = jsonOfSize(64 * 1024);

        HttpResponse<String> response = server.post("/api/echo", "application/json", body);

        assertEquals(200, response.statusCode());
    }

    @Test
    void rejectsBodyLargerThan64Kilobytes() throws Exception {
        String body = jsonOfSize(64 * 1024 + 1);

        HttpResponse<String> response = server.post("/api/echo", "application/json", body);

        assertEquals(413, response.statusCode());
    }

    /** ASCII だけで、ちょうど {@code size} バイトの JSON のオブジェクトを作る。 */
    private static String jsonOfSize(int size) {
        String prefix = "{\"email\":\"";
        String suffix = "\"}";
        String body = prefix + "a".repeat(size - prefix.length() - suffix.length()) + suffix;
        assertEquals(size, body.length());
        return body;
    }
}
