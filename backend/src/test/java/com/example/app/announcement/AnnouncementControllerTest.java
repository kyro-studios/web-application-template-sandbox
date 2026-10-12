package com.example.app.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/announcements} の試験 (Controller)。
 *
 * <p>Repository は、試験用のスタブに置き換える。SQL の振る舞い (公開前の除外、並び順、件数) は {@link AnnouncementRepositoryTest} で試す。
 * データベースを止めた状態は、接続できない場所を指す Jdbi で作る。
 */
class AnnouncementControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** バックエンドの E-04 の文言の、試験の定数。フロントエンドの文言と同じ文字列であることを、両方の試験で比べる */
    private static final String E04_MESSAGE = "お知らせを表示できませんでした。時間をおいて、もう一度開いてください";

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-11T15:00:00Z"), ZoneOffset.UTC);

    /** スタブが使う Jdbi。試験では接続しない */
    private static final Jdbi UNUSED_JDBI = Jdbi.create(() -> {
        throw new SQLException("試験では接続しない");
    });

    private static final String EMPTY_BODY = "{\"announcements\":[]}";

    private static final String FAILURE_BODY = "{\"error\":{\"code\":\"E-04\",\"message\":\"" + E04_MESSAGE + "\"}}";

    @Test
    void Cookie_無しと任意の_Cookie_付きで_同じ_200_の応答になる() {
        AnnouncementController controller = controllerReturning(List.of(sample("お知らせ", "本文", "2026-10-11T14:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
            Reply anonymous = reply(client.get(AnnouncementController.PATH));
            Reply withCookie = reply(client.get(AnnouncementController.PATH, b -> b.header("Cookie", "SESSION=expired")));

            assertEquals(200, anonymous.status());
            assertEquals(anonymous, withCookie);
        });
    }

    @Test
    void クエリ文字列を付けても_応答は変わらない() {
        AnnouncementController controller = controllerReturning(List.of(sample("お知らせ", "本文", "2026-10-11T14:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
            Reply plain = reply(client.get(AnnouncementController.PATH));

            assertEquals(plain, reply(client.get(AnnouncementController.PATH + "?limit=1")));
            assertEquals(plain, reply(client.get(AnnouncementController.PATH + "?now=2000-01-01T00:00:00Z")));
            assertEquals(plain, reply(client.get(AnnouncementController.PATH + "?all=true")));
        });
    }

    @Test
    void 応答の項目は_title_body_publishedDate_だけで_Cache_Control_は_no_store_になる() {
        AnnouncementController controller = controllerReturning(List.of(sample("お知らせ", "本文", "2026-10-11T14:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));
            JsonNode body = MAPPER.readTree(reply.body());

            assertEquals(Set.of("announcements"), fieldNames(body));
            JsonNode item = body.get("announcements").get(0);
            assertEquals(Set.of("title", "body", "publishedDate"), fieldNames(item));
            assertEquals("no-store", reply.cacheControl());
            assertTrue(reply.contentType().startsWith("application/json"), reply.contentType());
        });
    }

    @Test
    void 登録されたタイトルと本文は_script_を含めて_加工されずに返る() {
        String title = "  <script>alert(1)</script>  ";
        String body = "1 行目\r\n\n  <img src=x onerror=alert(1)>\n末尾";
        AnnouncementController controller = controllerReturning(List.of(sample(title, body, "2026-10-11T14:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
            JsonNode item = MAPPER.readTree(reply(client.get(AnnouncementController.PATH)).body())
                    .get("announcements").get(0);

            assertEquals(title, item.get("title").asText());
            assertEquals(body, item.get("body").asText());
        });
    }

    @Test
    void 公開日は_日本時間の年月日を_YYYY_MM_DD_で返す() {
        AnnouncementController controller = controllerReturning(List.of(sample("お知らせ", "本文", "2026-10-11T15:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
            JsonNode item = MAPPER.readTree(reply(client.get(AnnouncementController.PATH)).body())
                    .get("announcements").get(0);

            assertEquals("2026-10-12", item.get("publishedDate").asText());
        });
    }

    @Test
    void 公開中のお知らせが_0_件のときは_空の一覧を_200_で返す() {
        AnnouncementController controller = controllerReturning(List.of());

        JavalinTest.test(app(controller), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));

            assertEquals(200, reply.status());
            assertEquals(MAPPER.readTree(EMPTY_BODY), MAPPER.readTree(reply.body()));
        });
    }

    @Test
    void データベースに接続できないときは_503_と_E04_の固定の応答になり_内部の情報を含まない() throws IOException {
        AnnouncementController controller = new AnnouncementController(
                new AnnouncementService(new AnnouncementRepository(unreachableJdbi()), FIXED_CLOCK));

        JavalinTest.test(app(controller), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));

            assertEquals(503, reply.status());
            assertEquals("no-store", reply.cacheControl());
            assertEquals(MAPPER.readTree(FAILURE_BODY), MAPPER.readTree(reply.body()));
            for (String internal : List.of("jdbc", "postgres", "announcements", "SELECT", "Exception", "127.0.0.1", "Connection")) {
                assertFalse(reply.body().contains(internal), "応答に内部の情報が含まれている: " + internal);
            }
        });
    }

    @Test
    void 取得の途中で_想定外の例外が起きても_E04_の固定の応答になる() {
        AnnouncementRepository failing = new AnnouncementRepository(UNUSED_JDBI) {
            @Override
            public List<Announcement> findPublished(Instant displayTime, int limit) {
                throw new IllegalStateException("内部の情報: テーブルの行を読めない");
            }
        };
        AnnouncementController controller = new AnnouncementController(new AnnouncementService(failing, FIXED_CLOCK));

        JavalinTest.test(app(controller), (server, client) -> {
            Reply reply = reply(client.get(AnnouncementController.PATH));

            assertEquals(503, reply.status());
            assertEquals(MAPPER.readTree(FAILURE_BODY), MAPPER.readTree(reply.body()));
            assertFalse(reply.body().contains("内部の情報"));
        });
    }

    @Test
    void GET_以外のメソッドは定義されず_404_になる() {
        AnnouncementController controller = controllerReturning(List.of());

        JavalinTest.test(app(controller), (server, client) -> {
            assertEquals(404, client.post(AnnouncementController.PATH).code());
        });
    }

    @Test
    void 同時に複数のスレッドから取得しても_どれも_200_で同じ内容になる() throws Exception {
        AnnouncementController controller = controllerReturning(List.of(
                sample("新しい", "本文 1", "2026-10-11T14:00:00Z"),
                sample("古い", "本文 2", "2026-10-10T14:00:00Z")));

        JavalinTest.test(app(controller), (server, client) -> {
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
                    for (Reply reply : future.get()) {
                        distinct.add(reply);
                    }
                }

                assertEquals(1, distinct.size());
                Reply only = distinct.iterator().next();
                assertEquals(200, only.status());
                assertEquals(expected, only.body());
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private static Javalin app(AnnouncementController controller) {
        Javalin app = Javalin.create();
        controller.register(app);
        return app;
    }

    private static AnnouncementController controllerReturning(List<Announcement> announcements) {
        AnnouncementRepository repository = new AnnouncementRepository(UNUSED_JDBI) {
            @Override
            public List<Announcement> findPublished(Instant displayTime, int limit) {
                return announcements;
            }
        };
        return new AnnouncementController(new AnnouncementService(repository, FIXED_CLOCK));
    }

    private static Announcement sample(String title, String body, String publishedAt) {
        return new Announcement(title, body, OffsetDateTime.parse(publishedAt));
    }

    /** 接続を受け付けない場所を指す Jdbi。ポートは、空いていたものを使い、すぐに閉じる */
    private static Jdbi unreachableJdbi() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return Jdbi.create("jdbc:postgresql://127.0.0.1:" + port + "/app", "app_user", "unused");
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
