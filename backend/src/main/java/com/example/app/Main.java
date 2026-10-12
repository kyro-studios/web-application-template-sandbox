package com.example.app;

import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.web.WebServer;
import io.javalin.Javalin;
import java.time.Clock;
import org.flywaydb.core.api.FlywayException;

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
            Database database = new Database(config);
            Clock clock = Clock.systemUTC();
            app = new WebServer(config, clock).create();
        } catch (IllegalArgumentException | FlywayException e) {
            System.err.println("起動を中止しました: " + e.getMessage());
            System.exit(1);
            return;
        }
        app.start();
    }
}
