package com.example.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.announcement.AnnouncementController;
import com.example.app.announcement.AnnouncementRepository;
import com.example.app.announcement.AnnouncementService;
import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.testsupport.PostgresTestSupport;
import io.javalin.Javalin;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

/**
 * {@link WebServer#createHttps} が、HTTPS だけで待ち受けることを試験する (認証機能の設計 9.1 NFR-4)。
 */
class WebServerTlsTest {

    private static final String PATH = "/api/announcements";

    @Test
    void HTTPS_で応答し_HTTP_の要求には応答しない() throws Exception {
        int port = freePort();
        Map<String, String> env = new HashMap<>(PostgresTestSupport.env());
        env.put("APP_HTTPS_PORT", String.valueOf(port));
        AppConfig config = AppConfig.fromEnv(env);

        try (Database database = new Database(config)) {
            AnnouncementController controller = new AnnouncementController(
                    new AnnouncementService(new AnnouncementRepository(database.jdbi()), Clock.systemUTC()));
            Javalin app = WebServer.createHttps(config, controller).start(port);
            try {
                OkHttpClient https = httpsClient();
                try (Response response = https.newCall(new Request.Builder()
                        .url("https://localhost:" + port + PATH).build()).execute()) {
                    assertEquals(200, response.code());
                    assertTrue(response.body().string().contains("\"announcements\""));
                }

                OkHttpClient plain = new OkHttpClient();
                assertThrows(IOException.class, () -> {
                    try (Response response = plain.newCall(new Request.Builder()
                            .url("http://localhost:" + port + PATH).build()).execute()) {
                        assertEquals(200, response.code(), "HTTP で応答してはならない");
                    }
                });
            } finally {
                app.stop();
            }
        }
    }

    /** 試験の証明書を信頼する HTTPS のクライアント */
    private static OkHttpClient httpsClient() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate;
        try (InputStream in = Files.newInputStream(Path.of(PostgresTestSupport.TEST_CERT_PATH))) {
            certificate = (X509Certificate) factory.generateCertificate(in);
        }
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("test-cert", certificate);
        TrustManagerFactory factoryTm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factoryTm.init(trust);
        X509TrustManager trustManager = (X509TrustManager) factoryTm.getTrustManagers()[0];
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trustManager}, null);
        return new OkHttpClient.Builder().sslSocketFactory(context.getSocketFactory(), trustManager).build();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
