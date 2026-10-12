package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.testing.HttpsTestServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebServerTest {

    private static HttpsTestServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpsTestServer.start(app -> {
            app.get("/api/ok", ctx -> ctx.result("ok"));
            app.get("/api/error", ctx -> {
                throw new ApiException(ErrorCode.E06);
            });
        });
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.close();
    }

    @Test
    void respondsOverHttps() throws Exception {
        HttpResponse<String> response = server.get("/api/ok");

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
        String protocol = response.sslSession().orElseThrow().getProtocol();
        assertTrue(Set.of("TLSv1.2", "TLSv1.3").contains(protocol), protocol);
    }

    @Test
    void doesNotAcceptPlainHttp() throws Exception {
        try (HttpClient plainClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/ok"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            assertThrows(IOException.class, () -> plainClient.send(request, HttpResponse.BodyHandlers.ofString()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/login", "/api/ok", "/api/error", "/api/unknown"})
    void addsSecurityHeadersToEveryResponse(String path) throws Exception {
        HttpResponse<String> response = server.get(path);

        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("max-age=31536000", response.headers().firstValue("Strict-Transport-Security").orElse(null));
    }

    @Test
    void servesIndexHtmlAtRoot() throws Exception {
        HttpResponse<String> response = server.get("/");

        assertEquals(200, response.statusCode());
        assertEquals(HttpsTestServer.INDEX_HTML, response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/account/password", "/no/such/page.html"})
    void servesIndexHtmlForUnknownNonApiPaths(String path) throws Exception {
        HttpResponse<String> response = server.get(path);

        assertEquals(200, response.statusCode());
        assertEquals(HttpsTestServer.INDEX_HTML, response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/api/unknown", "/api/accounts/unknown/path"})
    void doesNotServeIndexHtmlForUnknownApiPaths(String path) throws Exception {
        HttpResponse<String> response = server.get(path);

        assertEquals(404, response.statusCode());
        assertNotEquals(HttpsTestServer.INDEX_HTML, response.body());
    }
}
