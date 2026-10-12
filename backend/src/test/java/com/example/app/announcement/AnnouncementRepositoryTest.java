package com.example.app.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.config.AppConfig;
import com.example.app.db.Database;
import com.example.app.testsupport.PostgresTestSupport;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 一覧を取り出す Repository の試験。
 *
 * <p>Testcontainers の PostgreSQL 16 に、本物のマイグレーションを適用して試す。読み取りはアプリケーション用の利用者で行い、
 * データの用意はマイグレーション用の利用者で行う (アプリケーション用の利用者は書き込めない)。
 */
class AnnouncementRepositoryTest {

    private static final Instant DISPLAY_TIME = Instant.parse("2026-10-11T15:00:00Z");

    /** PostgreSQL のエラーコード (SQLSTATE)。query_canceled は、問い合わせの時間切れによる取り消し */
    private static final String QUERY_CANCELED = "57014";

    private static final String INSERT_SQL =
            "INSERT INTO announcements (title, body, published_at_text) VALUES (:title, :body, :publishedAtText)";

    private Database database;
    private Jdbi owner;
    private AnnouncementRepository repository;

    @BeforeEach
    void マイグレーションを適用し_お知らせを空にする() {
        database = new Database(AppConfig.fromEnv(PostgresTestSupport.env()));
        owner = PostgresTestSupport.ownerJdbi();
        owner.useHandle(handle -> handle.execute("TRUNCATE announcements RESTART IDENTITY"));
        repository = new AnnouncementRepository(database.jdbi());
    }

    @AfterEach
    void 接続を閉じる() {
        database.close();
    }

    @Test
    void 公開日時が表示の時点より_1_秒後のお知らせは含めず_ちょうどと_1_秒前は含める() {
        publish("1 秒後", "2026-10-11T15:00:01Z");
        publish("ちょうど", "2026-10-11T15:00:00Z");
        publish("1 秒前", "2026-10-11T14:59:59Z");

        assertEquals(List.of("ちょうど", "1 秒前"), titles(DISPLAY_TIME, 20));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 0",
            "20, 0",
            "21, 0",
            "0, 25",
            "3, 25",
    })
    void 件数は公開中のお知らせの数で決まり_新しい順の最大_20_件を返す(int published, int upcoming) {
        for (int i = 0; i < published; i++) {
            publish("公開中 " + i, DISPLAY_TIME.minus(Duration.ofMinutes(i)).toString());
        }
        for (int i = 0; i < upcoming; i++) {
            publish("公開前 " + i, DISPLAY_TIME.plus(Duration.ofSeconds(i + 1)).toString());
        }

        List<String> expected = IntStream.range(0, Math.min(published, 20))
                .mapToObj(i -> "公開中 " + i)
                .toList();
        assertEquals(expected, titles(DISPLAY_TIME, 20));
    }

    @Test
    void 表記が違っても同じ時点のお知らせは_登録の順序が後のものを先に並べる() {
        Instant displayTime = Instant.parse("2026-10-12T02:00:00Z");
        publish("A (先に登録)", "2026-10-12T10:00:00+09:00");
        publish("B (後に登録)", "2026-10-12T01:00:00Z");

        assertEquals(List.of("B (後に登録)", "A (先に登録)"), titles(displayTime, 20));
    }

    @Test
    void 同じ時点のお知らせが_3_件あれば_登録の順序が新しいものから並ぶ() {
        Instant displayTime = Instant.parse("2026-10-12T02:00:00Z");
        publish("A", "2026-10-12T01:00:00Z");
        publish("B", "2026-10-12T10:00:00+09:00");
        publish("C", "2026-10-12T01:00:00.000000Z");

        assertEquals(List.of("C", "B", "A"), titles(displayTime, 20));
    }

    @Test
    void タイトルと本文が同じお知らせは_まとめず_すべて別々に返す() {
        publish("同じタイトル", "同じ本文", "2026-10-11T14:00:00Z");
        publish("同じタイトル", "同じ本文", "2026-10-11T14:00:00Z");
        publish("同じタイトル", "同じ本文", "2026-10-11T14:00:00Z");

        List<Announcement> result = repository.findPublished(DISPLAY_TIME, 20);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(a -> a.title().equals("同じタイトル") && a.body().equals("同じ本文")));
    }

    @Test
    void タイトルと本文は_前後の空白と改行を含めて加工せず_公開日時は同じ時点で返す() {
        String title = "  前後の空白  ";
        String body = "1 行目\n\n  2 行目\r\n<script>alert(1)</script>";
        publish(title, body, "2026-10-11T14:00:00+09:00");

        Announcement announcement = repository.findPublished(DISPLAY_TIME, 20).get(0);

        assertEquals(title, announcement.title());
        assertEquals(body, announcement.body());
        assertEquals(Instant.parse("2026-10-11T05:00:00Z"), announcement.publishedAt().toInstant());
    }

    @Test
    void 公開前のお知らせの情報は_結果に一切含まれない() {
        publish("公開前の見出し", "公開前の本文", "2026-10-11T15:00:01Z");
        publish("公開中の見出し", "公開中の本文", "2026-10-11T14:00:00Z");

        List<Announcement> result = repository.findPublished(DISPLAY_TIME, 20);

        assertEquals(1, result.size());
        assertEquals("公開中の見出し", result.get(0).title());
        assertEquals("公開中の本文", result.get(0).body());
    }

    @Test
    void 同時に取得しても_互いに影響せず_どの取得も同じ内容になる() throws Exception {
        for (int i = 0; i < 25; i++) {
            publish("公開中 " + i, DISPLAY_TIME.minus(Duration.ofMinutes(i)).toString());
        }
        List<String> expected = titles(DISPLAY_TIME, 20);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<List<List<String>>>> readers = IntStream.range(0, 8)
                    .mapToObj(n -> (Callable<List<List<String>>>) () -> {
                        List<List<String>> results = new ArrayList<>();
                        for (int i = 0; i < 20; i++) {
                            results.add(titles(DISPLAY_TIME, 20));
                        }
                        return results;
                    })
                    .toList();

            for (Future<List<List<String>>> future : executor.invokeAll(readers)) {
                for (List<String> result : future.get()) {
                    assertEquals(expected, result);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 登録の途中のお知らせは取得に含まれず_コミット後に_20_件が一度に見える() {
        owner.useTransaction(handle -> {
            insertPublished(handle, 25);

            assertTrue(repository.findPublished(DISPLAY_TIME, 20).isEmpty());
        });

        assertEquals(20, repository.findPublished(DISPLAY_TIME, 20).size());
    }

    @Test
    void 取得と登録を同時に行っても_一部だけの一覧にはならない() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> writer = executor.submit(() -> owner.useTransaction(handle -> insertPublished(handle, 25)));

            Set<Integer> sizes = new HashSet<>();
            while (!writer.isDone()) {
                sizes.add(repository.findPublished(DISPLAY_TIME, 20).size());
            }
            writer.get();
            sizes.add(repository.findPublished(DISPLAY_TIME, 20).size());

            assertTrue(Set.of(0, 20).containsAll(sizes), "取得の結果の件数: " + sizes);
            assertEquals(20, repository.findPublished(DISPLAY_TIME, 20).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 問い合わせは_2_秒で時間切れになり_取得は例外で終わる() {
        // 別の利用者が表を排他的に握ると、一覧の問い合わせは待たされる。時間切れで止まることを確かめる
        owner.useTransaction(handle -> {
            handle.execute("LOCK TABLE announcements IN ACCESS EXCLUSIVE MODE");

            long start = System.nanoTime();
            RuntimeException e = assertThrows(RuntimeException.class, () -> repository.findPublished(DISPLAY_TIME, 20));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertEquals(QUERY_CANCELED, sqlStateOf(e));
            assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0, "経過時間: " + elapsed);
        });
    }

    private void publish(String title, String publishedAtText) {
        publish(title, "本文", publishedAtText);
    }

    private void publish(String title, String body, String publishedAtText) {
        owner.useHandle(handle -> insert(handle, title, body, publishedAtText));
    }

    /** 公開中のお知らせを、新しいものから順に {@code count} 件登録する (タイトルは「公開中 0」、「公開中 1」…) */
    private static void insertPublished(Handle handle, int count) {
        for (int i = 0; i < count; i++) {
            insert(handle, "公開中 " + i, "本文", DISPLAY_TIME.minus(Duration.ofMinutes(i)).toString());
        }
    }

    private static void insert(Handle handle, String title, String body, String publishedAtText) {
        handle.createUpdate(INSERT_SQL)
                .bind("title", title)
                .bind("body", body)
                .bind("publishedAtText", publishedAtText)
                .execute();
    }

    private List<String> titles(Instant displayTime, int limit) {
        return repository.findPublished(displayTime, limit).stream()
                .map(Announcement::title)
                .toList();
    }

    private static String sqlStateOf(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
