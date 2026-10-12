package com.example.app.testing;

import com.example.app.config.AppConfig;
import com.example.app.web.WebServer;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * 試験のために、一時的な自己署名の証明書で {@link WebServer} を HTTPS で起動する。ポートは空いているものを使う。
 *
 * <p>{@link #client()} は、その証明書を信頼する HTTP クライアントを返す。
 */
public final class TestServer implements AutoCloseable {
    private final WebServer server;
    private final HttpClient client;

    private TestServer(WebServer server, HttpClient client) {
        this.server = server;
        this.client = client;
    }

    /**
     * @param workDir 証明書を書き出すディレクトリ (JUnit の {@code @TempDir} など)
     * @param staticDir 静的ファイルのディレクトリ
     * @param clock サーバーに渡す時計
     * @param routes 試験用のルート
     */
    public static TestServer start(Path workDir, Path staticDir, Clock clock, Consumer<Javalin> routes) {
        TestCertificate certificate = TestCertificate.create(workDir);
        AppConfig config = new AppConfig(
                0,
                certificate.certPath(),
                certificate.keyPath(),
                "jdbc:postgresql://localhost:5432/unused",
                "unused",
                "unused",
                "unused",
                "unused",
                AppConfig.DEFAULT_DB_POOL_SIZE,
                staticDir);
        WebServer server = new WebServer(config, clock, routes).start();
        HttpClient client = HttpClient.newBuilder()
                .sslContext(certificate.trustingSslContext())
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new TestServer(server, client);
    }

    public int port() {
        return server.port();
    }

    public HttpClient client() {
        return client;
    }

    /** {@code https://localhost:<port><path>} */
    public URI uri(String path) {
        return URI.create("https://localhost:" + port() + path);
    }

    @Override
    public void close() {
        client.close();
        server.stop();
    }
}
