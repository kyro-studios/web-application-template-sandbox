package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.app.testing.HttpsTestServer;
import com.example.app.testing.LogCapture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET = "secret-detail-hunter2";

    private static HttpsTestServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpsTestServer.start(app -> {
            app.get("/api/errors/{code}", ctx -> {
                throw new ApiException(ErrorCode.valueOf(ctx.pathParam("code")));
            });
            app.get("/api/invalid-input", ctx -> {
                throw ApiException.invalidInput(List.of(
                        new FieldError("email", "メールアドレスの形式で入力してください"),
                        new FieldError("password", "パスワードは 12 文字以上で入力してください")));
            });
            app.get("/api/unexpected", ctx -> {
                throw new IllegalStateException("database password is " + SECRET);
            });
        });
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.close();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "E01|E-01|400|入力に誤りがあります",
            "E02|E-02|400|パスワードが正しくありません",
            "E03|E-03|409|このメールアドレスは使えません",
            "E04|E-04|401|メールアドレスまたはパスワードが正しくありません",
            "E05|E-05|429|続けてログインに失敗したため、しばらくログインできません",
            "E06|E-06|401|ログインが必要です",
            "E07|E-07|409|既にログインしています",
            "E08|E-08|403|その操作は行えません",
            "E90|E-90|503|処理できませんでした。時間をおいてやり直してください"})
    void errorCodesMatchTheTable(ErrorCode errorCode, String code, int status, String message) {
        assertEquals(code, errorCode.code());
        assertEquals(status, errorCode.status());
        assertEquals(message, errorCode.message());
    }

    @Test
    void invalidInputHasFieldErrors() throws Exception {
        HttpResponse<String> response = server.get("/api/invalid-input");

        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode expected = MAPPER.readTree("""
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
                """);
        assertEquals(expected, MAPPER.readTree(response.body()));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = "E01", mode = EnumSource.Mode.EXCLUDE)
    void otherErrorsHaveNoFieldErrors(ErrorCode errorCode) throws Exception {
        HttpResponse<String> response = server.get("/api/errors/" + errorCode.name());

        assertEquals(errorCode.status(), response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        JsonNode expected = MAPPER.createObjectNode().set("error", MAPPER.createObjectNode()
                .put("code", errorCode.code())
                .put("message", errorCode.message()));
        assertEquals(expected, body);
        assertFalse(body.get("error").has("fieldErrors"));
    }

    @Test
    void tooManyFailuresHasNoRetryAfter() throws Exception {
        HttpResponse<String> response = server.get("/api/errors/E05");

        assertEquals(429, response.statusCode());
        assertTrue(response.headers().firstValue("Retry-After").isEmpty());
    }

    @Test
    void unexpectedExceptionBecomesE90WithoutCause() throws Exception {
        try (LogCapture logs = LogCapture.of(ErrorHandler.class)) {
            HttpResponse<String> response = server.get("/api/unexpected");

            assertEquals(503, response.statusCode());
            JsonNode expected = MAPPER.readTree("""
                    { "error": { "code": "E-90", "message": "処理できませんでした。時間をおいてやり直してください" } }
                    """);
            assertEquals(expected, MAPPER.readTree(response.body()));
            assertFalse(response.body().contains(SECRET));
            assertFalse(response.body().contains("IllegalStateException"));

            // ログには例外の種類とスタックトレースを書く
            ILoggingEvent event = logs.events().stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElseThrow();
            assertTrue(event.getFormattedMessage().contains(IllegalStateException.class.getName()));
            assertEquals(IllegalStateException.class.getName(), event.getThrowableProxy().getClassName());
            assertTrue(event.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
        }
    }
}
