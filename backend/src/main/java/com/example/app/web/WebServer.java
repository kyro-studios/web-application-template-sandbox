package com.example.app.web;

import com.example.app.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.community.ssl.TlsConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Javalin の設定 (HTTPS、静的ファイル、共通のヘッダー、エラーの応答、要求のログ)。
 */
public final class WebServer {

    /** 要求の本文の大きさの上限 (アーキテクチャ設計 5.1)。 */
    public static final long MAX_REQUEST_SIZE = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private WebServer() {
    }

    /**
     * Javalin を組み立てる。起動 ({@link Javalin#start()}) は呼び出す側で行う。
     */
    public static Javalin create(AppConfig config) {
        // HTTPS のコネクターだけを作る。HTTP (暗号化されていない) のコネクターは作らない (9.1)
        SslPlugin sslPlugin = new SslPlugin(ssl -> {
            ssl.pemFromPath(config.tlsCertPath().toString(), config.tlsKeyPath().toString());
            ssl.insecure = false;
            ssl.secure = true;
            ssl.securePort = config.httpsPort();
            ssl.http2 = false;
            // TLS 1.2 と TLS 1.3 だけを使う
            ssl.tlsConfig = TlsConfig.INTERMEDIATE;
        });

        Path staticDir = config.staticDir().toAbsolutePath().normalize();
        Path indexHtml = staticDir.resolve("index.html");
        RequestLogger requestLogger = new RequestLogger();

        Javalin app = Javalin.create(javalin -> {
            javalin.showJavalinBanner = false;
            javalin.registerPlugin(sslPlugin);
            javalin.http.maxRequestSize = MAX_REQUEST_SIZE;
            javalin.requestLogger.http((ctx, executionTimeMs) -> requestLogger.log(ctx, executionTimeMs));
            if (Files.isDirectory(staticDir)) {
                javalin.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = staticDir.toString();
                    staticFiles.location = Location.EXTERNAL;
                    staticFiles.headers = SecurityHeaders.HEADERS;
                });
            } else {
                log.warn("Static file directory does not exist: {}", staticDir);
            }
        });

        app.before(SecurityHeaders::apply);
        ErrorHandler.register(app);
        // SPA のルーティングのため、/api 以外の見つからないパスには index.html を返す
        app.error(HttpStatus.NOT_FOUND, ctx -> serveIndexHtml(ctx, indexHtml));
        return app;
    }

    private static void serveIndexHtml(Context ctx, Path indexHtml) throws IOException {
        if (isApiPath(ctx.path())) {
            return;
        }
        if (ctx.method() != HandlerType.GET && ctx.method() != HandlerType.HEAD) {
            return;
        }
        if (!Files.isRegularFile(indexHtml)) {
            return;
        }
        ctx.status(HttpStatus.OK)
                .contentType("text/html; charset=utf-8")
                .result(Files.readAllBytes(indexHtml));
    }

    /** API のパス ({@code /api} と、その下) かどうか。 */
    public static boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }
}
