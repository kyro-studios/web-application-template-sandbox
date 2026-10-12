package com.example.app;

import com.example.app.config.AppConfig;
import com.example.app.web.WebServer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 起動。設定を読み、各クラスを組み立てて Javalin を起動する (アーキテクチャ設計 3.2)。 */
public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {}

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromEnvironment(System.getenv());
        } catch (AppConfig.ConfigException e) {
            log.error(e.getMessage());
            System.exit(1);
            return;
        }

        // 日時は、すべてこの時計から求める (試験で時刻を進められるようにするため)
        Clock clock = Clock.systemUTC();

        WebServer server = new WebServer(config, clock, app -> {});
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        log.info("https://localhost:{} で待ち受けています", server.port());
    }
}
