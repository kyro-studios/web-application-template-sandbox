package com.example.app.web;

import com.example.app.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Consumer;

/**
 * Javalin の設定と、ルートの登録を行う。
 *
 * <p>HTTPS だけで待ち受け、HTTP のコネクターは作らない (認証機能の設計 9.1)。
 * フロントエンドの静的ファイルを配信し、API 以外の GET には {@code index.html} を返す (SPA のルーティングのため)。
 * CORS は設定しない (フロントエンドとバックエンドは同じオリジンから配信する)。
 */
public final class WebServer {

    private static final String INDEX_FILE = "index.html";

    private final AppConfig config;
    private final Clock clock;

    public WebServer(AppConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    /**
     * HTTPS だけで待ち受ける Javalin を作る。起動は呼び出し側 (Main) が行う。
     */
    public Javalin create() {
        return create(javalinConfig -> javalinConfig.registerPlugin(httpsPlugin()));
    }

    /**
     * 待ち受けの設定を差し込んで Javalin を作る。試験では、HTTP で待ち受ける設定を差し込む。
     *
     * @throws IllegalArgumentException 静的ファイルのディレクトリが無い場合 (起動を中止する)
     */
    Javalin create(Consumer<JavalinConfig> transport) {
        if (!Files.isDirectory(config.staticDir())) {
            throw new IllegalArgumentException(
                    "APP_STATIC_DIR のディレクトリが見つかりません: " + config.staticDir() + " (フロントエンドをビルドしてください)");
        }
        Javalin app = Javalin.create(javalinConfig -> {
            transport.accept(javalinConfig);
            javalinConfig.staticFiles.add(config.staticDir().toString(), Location.EXTERNAL);
        });
        // 機能ごとの API は、ここにルートを登録する。Service を組み立てるときは clock を渡す
        app.error(404, this::serveIndexForClientRoute);
        return app;
    }

    private SslPlugin httpsPlugin() {
        return new SslPlugin(conf -> {
            conf.pemFromPath(config.tlsCertPath(), config.tlsKeyPath());
            conf.securePort = config.httpsPort();
            conf.insecure = false;
        });
    }

    /** API 以外の GET に、React の画面 (index.html) を返す。それ以外は、Javalin の既定の 404 のままにする */
    private void serveIndexForClientRoute(Context ctx) {
        Path index = config.staticDir().resolve(INDEX_FILE);
        if (ctx.method() != HandlerType.GET || isApiPath(ctx.path()) || !Files.isRegularFile(index)) {
            return;
        }
        ctx.status(200).html(readIndex(index));
    }

    private static boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static String readIndex(Path index) {
        try {
            return Files.readString(index);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
