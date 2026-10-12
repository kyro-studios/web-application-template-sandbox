package com.example.app.web;

import com.example.app.announcement.AnnouncementController;
import io.javalin.Javalin;

/**
 * Javalin のアプリケーションを作り、各機能のルートを登録する。待ち受けは呼び出し側が行う。
 */
public final class WebServer {

    private WebServer() {
    }

    public static Javalin create(AnnouncementController announcements) {
        Javalin app = Javalin.create();
        announcements.register(app);
        return app;
    }
}
