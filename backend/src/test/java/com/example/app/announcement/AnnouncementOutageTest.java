package com.example.app.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.testsupport.PostgresTestSupport;
import com.example.app.web.WebServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * データベースを止めた状態の受け入れ試験。試験のために、専用の PostgreSQL のコンテナを起動し、途中で止める。
 */
class AnnouncementOutageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-11T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void データベースを止めると_503_と_E04_の固定の応答になり_内部の情報を含まない() throws IOException {
        PostgreSQLContainer<?> container = PostgresTestSupport.startContainer();
        try (Database database = new Database(AppConfig.fromEnv(PostgresTestSupport.env(container)))) {
            AnnouncementService service = new AnnouncementService(new AnnouncementRepository(database.jdbi()), FIXED_CLOCK);
            AnnouncementController controller = new AnnouncementController(service);

            container.stop();

            JavalinTest.test(WebServer.create(controller), (server, client) -> {
                Reply reply = reply(client.get(AnnouncementController.PATH));

                assertEquals(503, reply.status());
                assertEquals("no-store", reply.cacheControl());
                assertEquals(
                        MAPPER.readTree("{\"error\":{\"code\":\"E-04\",\"message\":\""
                                + AnnouncementAcceptanceTest.E04_MESSAGE + "\"}}"),
                        MAPPER.readTree(reply.body()));
                for (String internal : List.of("jdbc", "postgres", "announcements", "SELECT", "Exception", "127.0.0.1", "Connection")) {
                    assertFalse(reply.body().contains(internal), "応答に内部の情報が含まれている: " + internal);
                }
            });
        } finally {
            container.stop();
        }
    }

    private static Reply reply(Response response) throws IOException {
        try (response) {
            return new Reply(response.code(), response.header("Cache-Control"), response.body().string());
        }
    }

    private record Reply(int status, String cacheControl, String body) {
    }
}
