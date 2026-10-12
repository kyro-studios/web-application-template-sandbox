package com.example.app.web;

import com.example.app.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.community.ssl.TlsConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Javalin の設定 (アーキテクチャ設計 3.2、5.1、9.1)。
 *
 * <ul>
 *   <li>SSL プラグインで HTTPS だけを待ち受ける。HTTP のコネクターは作らない。TLS は 1.2 以上
 *   <li>{@code APP_STATIC_DIR} の静的ファイルを配信し、{@code /api} 以外の見つからないパスには {@code index.html} を返す
 *   <li>要求の本文の上限は 64 KB。{@code Content-Type} やルートによらず、すべての要求に {@link RequestBodyLimit} で適用する
 *   <li>すべての応答に {@link SecurityHeaders} のヘッダーを付ける
 *   <li>CORS の設定は行わない
 * </ul>
 */
public final class WebServer {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    /** 要求の本文の大きさの上限 (バイト)。 */
    public static final long MAX_REQUEST_SIZE = 64 * 1024;

    static final String API_PATH = "/api";
    private static final String INDEX_HTML = "index.html";

    private final Javalin app;

    /**
     * @param config 設定
     * @param clock 処理時間を求める時計
     * @param routes API のルートを登録する処理
     */
    public WebServer(AppConfig config, Clock clock, Consumer<Javalin> routes) {
        Path staticDir = config.staticDir().toAbsolutePath().normalize();
        RequestLogger requestLogger = new RequestLogger(clock);

        SslPlugin sslPlugin = new SslPlugin(ssl -> {
            ssl.pemFromPath(config.tlsCertPath().toString(), config.tlsKeyPath().toString());
            ssl.insecure = false;
            ssl.secure = true;
            ssl.securePort = config.httpsPort();
            ssl.tlsConfig = TlsConfig.INTERMEDIATE; // TLSv1.2 と TLSv1.3 だけ
        });

        this.app = Javalin.create(javalin -> {
            javalin.showJavalinBanner = false;
            javalin.registerPlugin(sslPlugin);
            javalin.http.maxRequestSize = MAX_REQUEST_SIZE;
            javalin.requestLogger.http((ctx, executionTimeMs) -> requestLogger.log(ctx));
            if (!Files.isDirectory(staticDir)) {
                log.warn("静的ファイルのディレクトリがありません: {}", staticDir);
            } else {
                javalin.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = staticDir.toString();
                    staticFiles.location = Location.EXTERNAL;
                    // 既定の Cache-Control (max-age=0) で上書きされないようにする
                    staticFiles.headers = SecurityHeaders.HEADERS;
                });
            }
        });

        app.before(ctx -> {
            requestLogger.start(ctx);
            SecurityHeaders.apply(ctx);
            RequestBodyLimit.enforce(ctx);
        });
        ErrorHandler.register(app);
        app.error(HttpStatus.NOT_FOUND, ctx -> serveIndexHtml(ctx, staticDir));
        routes.accept(app);
    }

    public WebServer start() {
        app.start();
        return this;
    }

    public void stop() {
        app.stop();
    }

    /** 待ち受けているポート。 */
    public int port() {
        return app.port();
    }

    /** SPA のルーティングのため、{@code /api} 以外の見つからないパスへの GET には {@code index.html} を返す。 */
    private static void serveIndexHtml(Context ctx, Path staticDir) {
        if (ctx.method() != HandlerType.GET || isApiPath(ctx.path())) {
            return;
        }
        Path indexHtml = staticDir.resolve(INDEX_HTML);
        if (!Files.isRegularFile(indexHtml)) {
            return;
        }
        try {
            ctx.status(HttpStatus.OK).contentType(ContentType.TEXT_HTML + "; charset=utf-8").result(Files.readAllBytes(indexHtml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isApiPath(String path) {
        return path.equals(API_PATH) || path.startsWith(API_PATH + "/");
    }
}
