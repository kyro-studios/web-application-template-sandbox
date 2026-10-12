package com.example.app;

import com.example.app.config.AppConfig;
import com.example.app.web.WebServer;
import io.javalin.Javalin;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 起動。設定を読み、各クラスを組み立てて Javalin を起動する (アーキテクチャ設計 3.2)。
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromEnvironment(System.getenv());
        } catch (AppConfig.ConfigException e) {
            log.error("Startup aborted: {}", e.getMessage());
            System.exit(1);
            return;
        }

        // 日時は、すべてこの Clock から求める。日時を扱うクラスには、組み立てるときにこれを渡す
        Clock clock = Clock.systemUTC();

        Javalin app = create(config, clock);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "shutdown"));
        app.start();
    }

    /**
     * 各クラスを組み立てる。API の登録は、後のタスクでここに加える。
     */
    static Javalin create(AppConfig config, Clock clock) {
        return WebServer.create(config);
    }
}
