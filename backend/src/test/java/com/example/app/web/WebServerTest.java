package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.testing.LogCapture;
import com.example.app.testing.MutableClock;
import com.example.app.testing.TestServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebServerTest {
    private static final String INDEX_HTML = "<!doctype html><title>app</title><div id=\"root\"></div>";

    @TempDir
    Path workDir;

    private final MutableClock clock = MutableClock.start();
    private TestServer server;

    @BeforeEach
    void startServer() throws IOException {
        Path staticDir = Files.createDirectories(workDir.resolve("static"));
        Files.writeString(staticDir.resolve("index.html"), INDEX_HTML);
        Files.createDirectories(staticDir.resolve("assets"));
        Files.writeString(staticDir.resolve("assets/app.js"), "console.log('app');");

        server = TestServer.start(workDir, staticDir, clock, app -> {
            app.get("/api/ok", ctx -> ctx.json(Map.of("message", "ok")));
            app.get("/api/items/{itemId}", ctx -> {
                clock.advance(Duration.ofMillis(150));
                ctx.json(Map.of("message", "ok"));
            });
            app.get("/api/forbidden", ctx -> {
                throw new ApiException(ErrorCode.E_08);
            });
            app.get("/api/unexpected", ctx -> {
                throw new IllegalStateException("boom");
            });
            app.post("/api/body", ctx -> ctx.json(Map.of("email", JsonBody.read(ctx).string("email").orElse(""))));
            app.post("/api/no-body", ctx -> ctx.json(Map.of("message", "ok")));
        });
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return server.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(server.uri(path)).GET().build());
    }

    @Test
    void HTTPSで応答する() throws Exception {
        HttpResponse<String> response = get("/api/ok");

        assertEquals(200, response.statusCode());
        assertEquals("https", response.uri().getScheme());
        assertTrue(response.sslSession().isPresent());
        assertEquals("{\"message\":\"ok\"}", response.body());
    }

    @Test
    void TLS12で接続できる() throws Exception {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] {"TLSv1.2"});
        try (HttpClient client = HttpClient.newBuilder()
                .sslContext(server.client().sslContext())
                .sslParameters(parameters)
                .build()) {
            HttpResponse<String> response =
                    client.send(HttpRequest.newBuilder(server.uri("/api/ok")).build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("TLSv1.2", response.sslSession().orElseThrow().getProtocol());
        }
    }

    @Test
    void HTTPでは接続できない() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/ok"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/ok", "/api/forbidden", "/api/unexpected", "/api/unknown", "/", "/account", "/assets/app.js"})
    void すべての応答に共通のヘッダーが付く(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"), path);
        assertEquals(List.of("max-age=31536000"), response.headers().allValues("Strict-Transport-Security"), path);
    }

    @Test
    void エラーの応答も共通のヘッダーが付き_5_2の形になる() throws Exception {
        HttpResponse<String> response = get("/api/forbidden");

        assertEquals(403, response.statusCode());
        assertEquals("{\"error\":{\"code\":\"E-08\",\"message\":\"その操作は行えません\"}}", response.body());
    }

    @Test
    void 想定外の例外はE90になる() throws Exception {
        HttpResponse<String> response = get("/api/unexpected");

        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("boom"), response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/account/password", "/unknown/deep/path", "/apix", "/index.html"})
    void API以外の未知のパスではindex_htmlを返す(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertEquals(200, response.statusCode(), path);
        assertEquals(INDEX_HTML, response.body(), path);
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"), path);
    }

    @Test
    void 静的ファイルを配信する() throws Exception {
        HttpResponse<String> response = get("/assets/app.js");

        assertEquals(200, response.statusCode());
        assertEquals("console.log('app');", response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/api/", "/api/unknown", "/api/accounts/x/y"})
    void APIの未知のパスではindex_htmlを返さない(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertEquals(404, response.statusCode(), path);
        assertFalse(response.body().contains(INDEX_HTML), path);
    }

    @Test
    void 上限ちょうどの本文は受け付ける() throws Exception {
        HttpResponse<String> response = postJson(jsonOfSize((int) WebServer.MAX_REQUEST_SIZE));

        assertEquals(200, response.statusCode());
        assertEquals("{\"email\":\"user@example.com\"}", response.body());
    }

    @Test
    void 上限を超える本文は受け付けない() throws Exception {
        HttpResponse<String> response = postJson(jsonOfSize((int) WebServer.MAX_REQUEST_SIZE + 1));

        assertEquals(413, response.statusCode());
        assertFalse(response.body().contains("user@example.com"));
    }

    @Test
    void ContentLengthの無い要求でも上限を超える本文は受け付けない() throws Exception {
        byte[] body = jsonOfSize((int) WebServer.MAX_REQUEST_SIZE + 1);
        // InputStream から送ると、Content-Length を付けずに chunked で送られる
        HttpRequest request = HttpRequest.newBuilder(server.uri("/api/body"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(413, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", ""})
    void ContentTypeがJSONでないContentLengthの無い要求でも上限を超える本文は受け付けない(String contentType) throws Exception {
        HttpResponse<String> response = postChunked("/api/body", contentType, jsonOfSize((int) WebServer.MAX_REQUEST_SIZE + 1));

        assertEquals(413, response.statusCode());
        assertFalse(response.body().contains("user@example.com"));
    }

    @Test
    void 本文を読まないルートでも上限を超える本文は受け付けない() throws Exception {
        HttpResponse<String> response =
                postChunked("/api/no-body", "application/json", jsonOfSize((int) WebServer.MAX_REQUEST_SIZE + 1));

        assertEquals(413, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", ""})
    void ContentTypeがJSONでない上限以内の本文は空のオブジェクトとして扱う(String contentType) throws Exception {
        HttpResponse<String> response = postChunked("/api/body", contentType, jsonOfSize((int) WebServer.MAX_REQUEST_SIZE));

        assertEquals(200, response.statusCode());
        assertEquals("{\"email\":\"\"}", response.body());
    }

    @Test
    void 要求のログにはメソッドとパスのテンプレートと状態と処理時間だけを書く() throws Exception {
        try (LogCapture logs = LogCapture.root()) {
            HttpRequest request = HttpRequest.newBuilder(server.uri("/api/items/secret-item-1?token=secret-query"))
                    .header("Cookie", "SESSION=secret-cookie")
                    .GET()
                    .build();
            assertEquals(200, send(request).statusCode());

            String line = logs.await(l -> l.startsWith("GET /api/items/"));
            assertEquals("GET /api/items/{itemId} 200 150ms", line);
            String all = logs.text();
            assertFalse(all.contains("secret"), all);
        }
    }

    @Test
    void ルートの無い要求はパスを書かずにログに書く() throws Exception {
        try (LogCapture logs = LogCapture.root()) {
            assertEquals(404, get("/api/secret-path").statusCode());
            assertEquals(200, get("/secret-page").statusCode());

            assertEquals("GET " + RequestLogger.NO_ROUTE + " 404 0ms", logs.await(l -> l.contains(" 404 ")));
            assertEquals("GET " + RequestLogger.NO_ROUTE + " 200 0ms", logs.await(l -> l.contains(" 200 ")));
            String all = logs.text();
            assertFalse(all.contains("secret"), all);
        }
    }

    private HttpResponse<String> postJson(byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(server.uri("/api/body"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request);
    }

    /** Content-Length を付けずに chunked で送る。{@code contentType} が空なら Content-Type を付けない。 */
    private HttpResponse<String> postChunked(String path, String contentType, byte[] body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(server.uri(path))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
        if (!contentType.isEmpty()) {
            request.header("Content-Type", contentType);
        }
        return send(request.build());
    }

    /** ちょうど {@code size} バイトの JSON のオブジェクトを作る。 */
    private static byte[] jsonOfSize(int size) {
        String prefix = "{\"email\":\"user@example.com\",\"padding\":\"";
        String suffix = "\"}";
        String json = prefix + "x".repeat(size - prefix.length() - suffix.length()) + suffix;
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        assertEquals(size, bytes.length);
        return bytes;
    }
}
