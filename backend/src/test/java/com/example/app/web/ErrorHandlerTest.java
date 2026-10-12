package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Javalin app() {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        ErrorHandler.register(app);
        app.get("/api/error/{code}", ctx -> {
            ErrorCode code = ErrorCode.valueOf(ctx.pathParam("code"));
            if (code == ErrorCode.E_01) {
                throw ApiException.invalidInput(List.of(
                        new FieldError("email", "メールアドレスの形式で入力してください"),
                        new FieldError("password", "パスワードは 12 文字以上で入力してください")));
            }
            throw new ApiException(code);
        });
        app.get("/api/unexpected", ctx -> {
            throw new IllegalStateException("secret-password-123",
                    new SQLException("token=secret-token-456"));
        });
        return app;
    }

    private static HttpResponse<String> get(Javalin server, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void E01は入力ごとの誤りを付けて返す() {
        JavalinTest.test(app(), (server, client) -> {
            HttpResponse<String> response = get(server, "/api/error/E_01");

            assertEquals(400, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
            JsonNode body = MAPPER.readTree(response.body());
            assertEquals(MAPPER.readTree("""
                    {
                      "error": {
                        "code": "E-01",
                        "message": "入力に誤りがあります",
                        "fieldErrors": [
                          { "field": "email", "message": "メールアドレスの形式で入力してください" },
                          { "field": "password", "message": "パスワードは 12 文字以上で入力してください" }
                        ]
                      }
                    }
                    """), body);
        });
    }

    @Test
    void E01以外は入力ごとの誤りを付けない() {
        JavalinTest.test(app(), (server, client) -> {
            for (ErrorCode code : Arrays.stream(ErrorCode.values()).filter(c -> c != ErrorCode.E_01).toList()) {
                HttpResponse<String> response = get(server, "/api/error/" + code.name());

                assertEquals(code.httpStatus(), response.statusCode(), code.code());
                JsonNode error = MAPPER.readTree(response.body()).get("error");
                assertEquals(code.code(), error.get("code").asText());
                assertEquals(code.message(), error.get("message").asText());
                assertNull(error.get("fieldErrors"), code.code());
                assertEquals(2, error.size(), code.code());
            }
        });
    }

    @Test
    void E05にRetryAfterを付けない() {
        JavalinTest.test(app(), (server, client) -> {
            HttpResponse<String> response = get(server, "/api/error/E_05");

            assertEquals(429, response.statusCode());
            assertTrue(response.headers().firstValue("Retry-After").isEmpty());
        });
    }

    @Test
    void 想定外の例外はE90になり_本文にもログにも原因のメッセージを含めない() {
        try (LogCapture logs = LogCapture.root()) {
            JavalinTest.test(app(), (server, client) -> {
                HttpResponse<String> response = get(server, "/api/unexpected");

                assertEquals(503, response.statusCode());
                assertEquals(MAPPER.readTree("""
                        { "error": { "code": "E-90", "message": "処理できませんでした。時間をおいてやり直してください" } }
                        """), MAPPER.readTree(response.body()));
                assertFalse(response.body().contains("secret"), response.body());
                assertFalse(response.body().contains("IllegalStateException"), response.body());
            });

            String log = logs.text();
            // 例外の種類とスタックトレースは書く
            assertTrue(log.contains("java.lang.IllegalStateException"), log);
            assertTrue(log.contains("Caused by: java.sql.SQLException"), log);
            assertTrue(log.contains("\tat "), log);
            // 例外のメッセージは書かない
            assertFalse(log.contains("secret-password-123"), log);
            assertFalse(log.contains("secret-token-456"), log);
        }
    }

    @Test
    void E01は入力ごとの誤りが無ければ作れない() {
        assertThrows(IllegalArgumentException.class, () -> ApiException.invalidInput(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ApiException(ErrorCode.E_01));
    }

    @Test
    void 番号ごとのHTTPの状態とメッセージ() {
        assertEquals(400, ErrorCode.E_01.httpStatus());
        assertEquals(400, ErrorCode.E_02.httpStatus());
        assertEquals(409, ErrorCode.E_03.httpStatus());
        assertEquals(401, ErrorCode.E_04.httpStatus());
        assertEquals(429, ErrorCode.E_05.httpStatus());
        assertEquals(401, ErrorCode.E_06.httpStatus());
        assertEquals(409, ErrorCode.E_07.httpStatus());
        assertEquals(403, ErrorCode.E_08.httpStatus());
        assertEquals(503, ErrorCode.E_90.httpStatus());
        assertEquals("パスワードが正しくありません", ErrorCode.E_02.message());
        assertEquals("このメールアドレスは使えません", ErrorCode.E_03.message());
        assertEquals("メールアドレスまたはパスワードが正しくありません", ErrorCode.E_04.message());
        assertEquals("続けてログインに失敗したため、しばらくログインできません", ErrorCode.E_05.message());
        assertEquals("ログインが必要です", ErrorCode.E_06.message());
        assertEquals("既にログインしています", ErrorCode.E_07.message());
        assertEquals("その操作は行えません", ErrorCode.E_08.message());
    }
}
