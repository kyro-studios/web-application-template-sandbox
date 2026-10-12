package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.testing.LogCapture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonBodyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VALID = "{\"email\":\"user@example.com\",\"password\":\"correct horse battery\"}";

    /** 読んだ結果を返す試験用のルート。入力が無ければ null を返す。 */
    private static Javalin app() {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.post("/api/echo", ctx -> {
            JsonBody body = JsonBody.read(ctx);
            // 同じ要求で 2 回読んでも同じ結果になる
            JsonBody again = JsonBody.read(ctx);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("email", body.string("email").orElse(null));
            result.put("password", body.string("password").orElse(null));
            result.put("hasAccountId", body.has("accountId"));
            result.put("same", again.string("email").equals(body.string("email")));
            ctx.json(result);
        });
        return app;
    }

    private static JsonNode post(Javalin server, String contentType, String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/echo"))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (contentType != null) {
                request.header("Content-Type", contentType);
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            JsonNode result = MAPPER.readTree(response.body());
            assertTrue(result.get("same").asBoolean());
            return result;
        }
    }

    private static void assertEmpty(JsonNode result) {
        assertTrue(result.get("email").isNull(), result.toString());
        assertTrue(result.get("password").isNull(), result.toString());
        assertFalse(result.get("hasAccountId").asBoolean(), result.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/json; charset=utf-8", "Application/JSON;charset=UTF-8"})
    void ContentTypeがJSONなら入力を読む(String contentType) {
        JavalinTest.test(app(), (server, client) -> {
            JsonNode result = post(server, contentType, VALID);

            assertEquals("user@example.com", result.get("email").asText());
            assertEquals("correct horse battery", result.get("password").asText());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "application/jsonx", "application/merge-patch+json"})
    void ContentTypeがJSONでなければ本文が無いものとして扱う(String contentType) {
        JavalinTest.test(app(), (server, client) -> assertEmpty(post(server, contentType, VALID)));
    }

    @Test
    void ContentTypeが無ければ本文が無いものとして扱う() {
        JavalinTest.test(app(), (server, client) -> assertEmpty(post(server, null, VALID)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "{\"email\":\"user@example.com\"",
        "{email:\"user@example.com\"}",
        "{\"email\":\"user@example.com\"} {}",
        "not json",
    })
    void JSONとして読めなければ本文が無いものとして扱う(String body) {
        JavalinTest.test(app(), (server, client) -> assertEmpty(post(server, "application/json", body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[" + VALID + "]", "\"user@example.com\"", "123", "null", "true"})
    void JSONのオブジェクトでなければ本文が無いものとして扱う(String body) {
        JavalinTest.test(app(), (server, client) -> assertEmpty(post(server, "application/json", body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "null", "true", "{}", "[]", "[\"user@example.com\"]"})
    void 値が文字列でない入力は無いものとして扱う(String value) {
        JavalinTest.test(app(), (server, client) -> {
            JsonNode result = post(server, "application/json",
                    "{\"email\":" + value + ",\"password\":\"correct horse battery\"}");

            assertTrue(result.get("email").isNull(), result.toString());
            assertEquals("correct horse battery", result.get("password").asText());
        });
    }

    @Test
    void 空文字列は文字列として読む() {
        JavalinTest.test(app(), (server, client) -> {
            JsonNode result = post(server, "application/json", "{\"email\":\"\"}");

            assertEquals("", result.get("email").asText());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "null", "\"x\""})
    void 項目があるかは値の種類によらず分かる(String value) {
        JavalinTest.test(app(), (server, client) -> {
            JsonNode result = post(server, "application/json", "{\"accountId\":" + value + "}");

            assertTrue(result.get("hasAccountId").asBoolean());
        });
    }

    @Test
    void 読めなかった場合に例外の種類だけをログに書き_入力を書かない() {
        try (LogCapture logs = LogCapture.root()) {
            JavalinTest.test(app(), (server, client) ->
                    assertEmpty(post(server, "application/json", "{\"password\":\"secret-password-123\" broken")));

            String log = logs.text();
            assertTrue(log.contains("com.fasterxml.jackson"), log);
            assertFalse(log.contains("secret-password-123"), log);
        }
    }
}
