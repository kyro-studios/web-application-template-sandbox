package com.example.app;

import com.example.app.config.AppConfig;
import com.example.app.web.WebServer;
import io.javalin.Javalin;
import java.time.Clock;

/**
 * 起動の入口。設定を読み、各クラスを組み立てて、Javalin を起動する。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Javalin app;
        try {
            AppConfig config = AppConfig.fromEnv(System.getenv());
            Clock clock = Clock.systemUTC();
            app = new WebServer(config, clock).create();
        } catch (IllegalArgumentException e) {
            System.err.println("起動を中止しました: " + e.getMessage());
            System.exit(1);
            return;
        }
        app.start();
    }
}
