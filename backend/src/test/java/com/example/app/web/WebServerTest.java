package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebServerTest {

    private static final String INDEX_HTML = "<!doctype html><title>app</title>";
    private static final String APP_JS = "console.log('app');";

    @TempDir
    Path staticDir;

    @BeforeEach
    void setUpStaticFiles() throws IOException {
        Files.writeString(staticDir.resolve("index.html"), INDEX_HTML);
        Files.createDirectories(staticDir.resolve("assets"));
        Files.writeString(staticDir.resolve("assets/app.js"), APP_JS);
    }

    /** HTTP で待ち受ける Javalin を作る。試験では、HTTPS の設定を外す */
    private Javalin app() {
        return app(staticDir);
    }

    private static Javalin app(Path dir) {
        Map<String, String> env = Map.of(
                "APP_TLS_CERT_PATH", "certs/localhost.pem",
                "APP_TLS_KEY_PATH", "certs/localhost-key.pem",
                "APP_DB_URL", "jdbc:postgresql://localhost:5432/app",
                "APP_DB_USER", "app_user",
                "APP_DB_PASSWORD", "app-user-secret",
                "APP_DB_MIGRATION_USER", "app_owner",
                "APP_DB_MIGRATION_PASSWORD", "app-owner-secret",
                "APP_STATIC_DIR", dir.toString());
        return new WebServer(AppConfig.fromEnv(env), Clock.systemUTC()).create(javalinConfig -> { });
    }

    @Test
    void 静的ファイルのディレクトリが無い場合は例外になり_起動しない() {
        Path missing = staticDir.resolve("missing");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> app(missing));

        assertTrue(e.getMessage().contains("APP_STATIC_DIR"), e.getMessage());
    }

    @Test
    void ルートのパスはindex_htmlを返す() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.get("/")) {
                assertEquals(200, response.code());
                assertEquals(INDEX_HTML, response.body().string());
            }
        });
    }

    @Test
    void API以外の未知のパスはindex_htmlを返す() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.get("/login")) {
                assertEquals(200, response.code());
                assertEquals(INDEX_HTML, response.body().string());
            }
        });
    }

    @Test
    void 静的ファイルはそのまま返す() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.get("/assets/app.js")) {
                assertEquals(200, response.code());
                assertEquals(APP_JS, response.body().string());
            }
        });
    }

    @Test
    void APIのパスは_index_htmlを返さず404になる() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.get("/api/unknown")) {
                assertEquals(404, response.code());
                assertFalse(response.body().string().contains("<title>app</title>"));
            }
            try (Response response = client.get("/api")) {
                assertEquals(404, response.code());
            }
        });
    }

    @Test
    void GET以外の未知のパスはindex_htmlを返さない() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.post("/login", null)) {
                assertEquals(404, response.code());
                assertFalse(response.body().string().contains("<title>app</title>"));
            }
        });
    }

    @Test
    void CORSのヘッダーを付けない() {
        JavalinTest.test(app(), (server, client) -> {
            try (Response response = client.get("/")) {
                assertNull(response.header("Access-Control-Allow-Origin"));
            }
        });
    }
}
