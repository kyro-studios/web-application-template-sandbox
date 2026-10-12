package com.example.app.web;

import com.example.app.announcement.AnnouncementController;
import com.example.app.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;

/**
 * Javalin のアプリケーションを作り、各機能のルートを登録する。待ち受けは呼び出し側が行う。
 *
 * <p>本番は {@link #createHttps} を使い、HTTP のコネクターを作らずに HTTPS だけで待ち受ける (認証機能の設計 9.1 NFR-4)。
 */
public final class WebServer {

    private WebServer() {
    }

    /** HTTPS だけで待ち受けるアプリケーションを作る。証明書と秘密鍵は、設定の PEM ファイルから読む */
    public static Javalin createHttps(AppConfig config, AnnouncementController announcements) {
        Javalin app = Javalin.create(javalin -> javalin.registerPlugin(new SslPlugin(ssl -> {
            ssl.pemFromPath(config.tlsCertPath(), config.tlsKeyPath());
            ssl.securePort = config.httpsPort();
            ssl.insecure = false;
            ssl.secure = true;
            ssl.http2 = false;
        })));
        announcements.register(app);
        return app;
    }

    /** ルートだけを登録する。試験では、TLS を付けずに使う */
    public static Javalin create(AnnouncementController announcements) {
        Javalin app = Javalin.create();
        announcements.register(app);
        return app;
    }
}
