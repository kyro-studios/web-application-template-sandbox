package com.example.app;

import com.example.app.announcement.AnnouncementController;
import com.example.app.announcement.AnnouncementRepository;
import com.example.app.announcement.AnnouncementService;
import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.web.WebServer;
import java.time.Clock;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnv(System.getenv());
        Database database = new Database(config);
        Runtime.getRuntime().addShutdownHook(new Thread(database::close));

        AnnouncementRepository announcementRepository = new AnnouncementRepository(database.jdbi());
        AnnouncementService announcementService = new AnnouncementService(announcementRepository, Clock.systemUTC());
        AnnouncementController announcementController = new AnnouncementController(announcementService);

        WebServer.createHttps(config, announcementController).start(config.httpsPort());
    }
}
