package com.example.app.testing;

import com.example.app.config.AppConfig;
import com.example.app.web.WebServer;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 試験の中で、一時的な自己署名の証明書を使って HTTPS のサーバーを起動する。
 *
 * <p>{@link WebServer#create(AppConfig)} で組み立てたサーバーに、試験用のルートを加えて起動する。
 * 静的ファイルのディレクトリには {@link #INDEX_HTML} を置く。
 */
public final class HttpsTestServer implements AutoCloseable {

    public static final String INDEX_HTML = "<!doctype html><html><head><title>test</title></head><body></body></html>";

    private final Path dir;
    private final Javalin app;
    private final int port;
    private final HttpClient client;

    private HttpsTestServer(Path dir, Javalin app, int port, HttpClient client) {
        this.dir = dir;
        this.app = app;
        this.port = port;
        this.client = client;
    }

    public static HttpsTestServer start(Consumer<Javalin> routes) throws Exception {
        Path dir = Files.createTempDirectory("https-test-server");
        TestCertificate certificate = TestCertificate.create(dir);
        Path staticDir = Files.createDirectory(dir.resolve("static"));
        Files.writeString(staticDir.resolve("index.html"), INDEX_HTML);

        int port = freePort();
        Map<String, String> env = new HashMap<>(requiredEnvironment());
        env.put("APP_HTTPS_PORT", String.valueOf(port));
        env.put("APP_TLS_CERT_PATH", certificate.certificatePem().toString());
        env.put("APP_TLS_KEY_PATH", certificate.privateKeyPem().toString());
        env.put("APP_STATIC_DIR", staticDir.toString());
        AppConfig config = AppConfig.fromEnvironment(env);

        Javalin app = WebServer.create(config);
        routes.accept(app);
        app.start();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(certificate.clientSslContext())
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new HttpsTestServer(dir, app, port, client);
    }

    /** {@link AppConfig} の必須の環境変数に、試験用の値を入れたもの。 */
    public static Map<String, String> requiredEnvironment() {
        return Map.of(
                "APP_TLS_CERT_PATH", "/tmp/cert.pem",
                "APP_TLS_KEY_PATH", "/tmp/key.pem",
                "APP_DB_URL", "jdbc:postgresql://localhost:5432/app",
                "APP_DB_USER", "app_user",
                "APP_DB_PASSWORD", "app-user-password",
                "APP_DB_MIGRATION_USER", "app_owner",
                "APP_DB_MIGRATION_PASSWORD", "app-owner-password");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public int port() {
        return port;
    }

    public URI uri(String path) {
        return URI.create("https://localhost:" + port + path);
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** {@code contentType} が null なら、Content-Type を付けずに送る。 */
    public HttpResponse<String> post(String path, String contentType, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() throws IOException {
        try {
            app.stop();
            client.close();
        } finally {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
