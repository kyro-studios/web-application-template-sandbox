package com.example.app.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.testsupport.PostgresTestSupport;
import com.example.app.web.WebServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import okhttp3.Response;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/announcements} の受け入れ試験。
 *
 * <p>Testcontainers の PostgreSQL に本物のマイグレーションを適用し、Repository、Service、Controller を実際に組み立てて通す。
 * スタブは使わない。データベースを止める試験は {@link AnnouncementOutageTest} で行う。
 */
class AnnouncementAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** バックエンドの E-04 の文言の、試験の定数。フロントエンドの文言と同じ文字列であることを、両方の試験で比べる */
    static final String E04_MESSAGE = "お知らせを表示できませんでした。時間をおいて、もう一度開いてください";

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-11T15:00:00Z"), ZoneOffset.UTC);

    private static final String INSERT_SQL =
            "INSERT INTO announcements (title, body, published_at_text) VALUES (:title, :body, :publishedAtText)";

    private static Database database;
    private static Jdbi owner;
    private static AnnouncementController controller;

    @BeforeAll
    static void 実際のデータベースと_組み立てた_API_を用意する() {
        database = new Database(AppConfig.fromEnv(PostgresTestSupport.env()));
        owner = PostgresTestSupport.ownerJdbi();
        AnnouncementService service = new AnnouncementService(new AnnouncementRepository(database.jdbi()), FIXED_CLOCK);
        controller = new AnnouncementController(service);
    }

    @AfterAll
    static void 接続を閉じる() {
        database.close();
    }

    @BeforeEach
    void お知らせを空にする() {
        owner.useHandle(handle -> handle.execute("TRUNCATE announcements RESTART IDENTITY"));
    }

    @Test
    void Cookie_無しと任意の_Cookie_付きで_同じ_200_の応答になる() {
        publish("お知らせ", "本文", "2026-10-11T14:00:00Z");

        JavalinTest.test(app(), (server, client) -> {
            Reply anonymous = reply(client.get(AnnouncementController.PATH));
            Reply withCookie = reply(client.get(AnnouncementController.PATH, b -> b.header("Cookie", "SESSION=expired")));

            assertEquals(200, anonymous.status());
            assertEquals(anonymous, withCookie);
        });
    }

    @Test
    void クエリ文字列を付けても_結果は変わらない() {
        publish("お知らせ", "本文", "2026-10-11T14:00:00Z");

        JavalinTest.test(app(), (server, client) -> {
            Reply plain = reply(client.get(AnnouncementController.PATH));

            assertEquals(plain, reply(client.get(AnnouncementController.PATH + "?limit=1")));
            assertEquals(plain, reply(client.get(AnnouncementController.PATH + "?now=2000-01-01T00:00:00Z")));
        });
    }

    @Test
    void 応答の項目は_3_つだけで_Cache_Control_は_no_store_になる() {
        publish("お知らせ", "本文", "2026-10-11T14:00:00Z");

        JavalinTest.test(app(), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));
            JsonNode body = MAPPER.readTree(reply.body());

            assertEquals(Set.of("announcements"), fieldNames(body));
            assertEquals(Set.of("title", "body", "publishedDate"), fieldNames(body.get("announcements").get(0)));
            assertEquals("no-store", reply.cacheControl());
        });
    }

    @Test
    void 公開前のお知らせの_title_と_body_は_応答に現れない() {
        publish("公開中の見出し", "公開中の本文", "2026-10-11T14:00:00Z");
        publish("公開前の見出し ZZQ1", "公開前の本文 ZZQ2", "2026-10-11T15:00:01Z");

        JavalinTest.test(app(), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));

            assertEquals(1, MAPPER.readTree(reply.body()).get("announcements").size());
            assertFalse(reply.body().contains("ZZQ1"));
            assertFalse(reply.body().contains("ZZQ2"));
        });
    }

    @Test
    void 登録されたタイトルと本文は_script_を含めて_加工されずに返る() {
        String title = "  <script>alert(1)</script>  ";
        String body = "1 行目\r\n\n  <img src=x onerror=alert(1)>\n末尾";
        publish(title, body, "2026-10-11T14:00:00Z");

        JavalinTest.test(app(), (server, client) -> {
            JsonNode item = MAPPER.readTree(reply(client.get(AnnouncementController.PATH)).body())
                    .get("announcements").get(0);

            assertEquals(title, item.get("title").asText());
            assertEquals(body, item.get("body").asText());
        });
    }

    @Test
    void 公開中のお知らせが_0_件なら_空の一覧を_200_で返す() {
        publish("公開前", "本文", "2026-10-11T15:00:01Z");

        JavalinTest.test(app(), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));

            assertEquals(200, reply.status());
            assertEquals(MAPPER.readTree("{\"announcements\":[]}"), MAPPER.readTree(reply.body()));
        });
    }

    @Test
    void 同時に複数のスレッドから取得しても_どれも_200_で同じ内容になる() throws Exception {
        for (int i = 0; i < 25; i++) {
            publish("公開中 " + i, "本文 " + i, Instant.parse("2026-10-11T14:00:00Z").minus(Duration.ofMinutes(i)).toString());
        }

        JavalinTest.test(app(), (server, client) -> {
            String expected = reply(client.get(AnnouncementController.PATH)).body();

            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                List<Callable<List<Reply>>> readers = IntStream.range(0, 8)
                        .mapToObj(n -> (Callable<List<Reply>>) () -> {
                            List<Reply> replies = new ArrayList<>();
                            for (int i = 0; i < 10; i++) {
                                replies.add(reply(client.get(AnnouncementController.PATH)));
                            }
                            return replies;
                        })
                        .toList();

                Set<Reply> distinct = new HashSet<>();
                for (Future<List<Reply>> future : executor.invokeAll(readers)) {
                    distinct.addAll(future.get());
                }

                assertEquals(1, distinct.size());
                Reply only = distinct.iterator().next();
                assertEquals(200, only.status());
                assertEquals(expected, only.body());
                assertEquals(20, MAPPER.readTree(expected).get("announcements").size());
                assertTrue(expected.contains("公開中 0"));
            } finally {
                executor.shutdownNow();
            }
        });
    }

    /** Main と同じく WebServer でルートを登録したアプリケーション */
    private static Javalin app() {
        return WebServer.create(controller);
    }

    private static void publish(String title, String body, String publishedAtText) {
        owner.useHandle(handle -> handle.createUpdate(INSERT_SQL)
                .bind("title", title)
                .bind("body", body)
                .bind("publishedAtText", publishedAtText)
                .execute());
    }

    private static Reply reply(Response response) throws IOException {
        try (response) {
            return new Reply(
                    response.code(),
                    response.header("Cache-Control"),
                    response.header("Content-Type"),
                    response.body().string());
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** 応答の、試験で比べる部分 */
    record Reply(int status, String cacheControl, String contentType, String body) {
    }
}
