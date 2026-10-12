package com.example.app.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.config.AppConfig;
import com.example.app.testsupport.PostgresTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * お知らせの一覧のマイグレーション (V35_1) と、開発用のサンプルの SQL の試験。
 *
 * <p>本物のマイグレーションを、Testcontainers の PostgreSQL 16 に適用して試す。制約の試験は、テーブルの所有者 (マイグレーション用の利用者)
 * で行う。アプリケーション用の利用者は書き込めないため、制約ではなく権限の違反で失敗してしまう。失敗の理由は SQLSTATE で確かめる。
 */
class AnnouncementsMigrationTest {

    private static final String SAMPLE_SQL = "/announcements/sample.sql";

    /** PostgreSQL のエラーコード (SQLSTATE)。https://www.postgresql.org/docs/16/errcodes-appendix.html */
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final String CHECK_VIOLATION = "23514";
    private static final String DATETIME_FIELD_OVERFLOW = "22008";
    private static final String GENERATED_ALWAYS = "428C9";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static final String INSERT_SQL =
            "INSERT INTO announcements (title, body, published_at_text) VALUES (:title, :body, :publishedAtText)";

    @BeforeEach
    void マイグレーションを適用し_お知らせを空にする() {
        try (Database database = new Database(config())) {
            PostgresTestSupport.ownerJdbi().useHandle(handle -> handle.execute("TRUNCATE announcements RESTART IDENTITY"));
        }
    }

    static Stream<Arguments> invalidPublishedAtTexts() {
        return Stream.of(
                Arguments.of(null, NOT_NULL_VIOLATION),
                Arguments.of("2026-10-12T00:00:00", CHECK_VIOLATION),
                Arguments.of("2026-10-12 00:00:00", CHECK_VIOLATION),
                Arguments.of("infinity", CHECK_VIOLATION),
                Arguments.of("2026-10-12 00:00:00+09:00", CHECK_VIOLATION),
                Arguments.of("2026-02-30T00:00:00Z", DATETIME_FIELD_OVERFLOW));
    }

    @ParameterizedTest
    @MethodSource("invalidPublishedAtTexts")
    void 公開日時が不正な値は_INSERT_が失敗する(String publishedAtText, String sqlState) {
        Jdbi owner = PostgresTestSupport.ownerJdbi();

        assertSqlState(sqlState, () -> insert(owner, "タイトル", "本文", publishedAtText));
    }

    @ParameterizedTest
    @MethodSource("invalidPublishedAtTexts")
    void 公開日時が不正な値は_UPDATE_が失敗し_元の行は変わらない(String publishedAtText, String sqlState) {
        Jdbi owner = PostgresTestSupport.ownerJdbi();
        insert(owner, "2026-10-11T15:00:00Z");

        assertSqlState(sqlState, () -> owner.useHandle(handle -> handle.createUpdate(
                        "UPDATE announcements SET published_at_text = :publishedAtText")
                .bind("publishedAtText", publishedAtText)
                .execute()));

        List<String> publishedAtTexts = owner.withHandle(handle -> handle.createQuery(
                        "SELECT published_at_text FROM announcements ORDER BY id")
                .mapTo(String.class)
                .list());
        assertEquals(List.of("2026-10-11T15:00:00Z"), publishedAtTexts);
    }

    @Test
    void published_at_に直接登録すると失敗する() {
        Jdbi owner = PostgresTestSupport.ownerJdbi();

        assertSqlState(GENERATED_ALWAYS, () -> owner.useHandle(handle -> handle.createUpdate(
                        "INSERT INTO announcements (title, body, published_at_text, published_at)"
                                + " VALUES (:title, :body, :publishedAtText, :publishedAt)")
                .bind("title", "タイトル")
                .bind("body", "本文")
                .bind("publishedAtText", "2026-10-11T15:00:00Z")
                .bind("publishedAt", OffsetDateTime.parse("2026-10-11T15:00:00Z"))
                .execute()));
    }

    @Test
    void 同じ時点を表す日本時間の値と_UTC_の値は_published_at_で同じ時点になる() {
        Jdbi owner = PostgresTestSupport.ownerJdbi();
        insert(owner, "2026-10-12T00:00:00+09:00");
        insert(owner, "2026-10-11T15:00:00Z");

        List<OffsetDateTime> publishedAts = owner.withHandle(handle -> handle.createQuery(
                        "SELECT published_at FROM announcements ORDER BY id")
                .mapTo(OffsetDateTime.class)
                .list());

        assertEquals(OffsetDateTime.parse("2026-10-11T15:00:00Z").toInstant(), publishedAts.get(0).toInstant());
        assertEquals(publishedAts.get(0).toInstant(), publishedAts.get(1).toInstant());
    }

    @Test
    void アプリケーション用の利用者は_INSERT_UPDATE_DELETE_が失敗し_SELECT_は成功する() {
        Jdbi owner = PostgresTestSupport.ownerJdbi();
        insert(owner, "2026-10-11T15:00:00Z");

        try (Database database = new Database(config())) {
            Jdbi app = database.jdbi();

            assertSqlState(INSUFFICIENT_PRIVILEGE, () -> insert(app, "2026-10-12T00:00:00+09:00"));
            assertSqlState(INSUFFICIENT_PRIVILEGE, () -> app.useHandle(handle -> handle.execute(
                    "UPDATE announcements SET title = '変更後'")));
            assertSqlState(INSUFFICIENT_PRIVILEGE, () -> app.useHandle(handle -> handle.execute(
                    "DELETE FROM announcements")));

            long count = app.withHandle(handle -> handle.createQuery("SELECT count(*) FROM announcements")
                    .mapTo(Long.class)
                    .one());
            assertEquals(1L, count);
        }
    }

    @Test
    void タイトルと本文に長さの制約と_UNIQUE_は無く_同じ内容を別々に登録できる() {
        Jdbi owner = PostgresTestSupport.ownerJdbi();
        String longTitle = "あ".repeat(101);
        String longBody = "い".repeat(2_001);
        insert(owner, longTitle, longBody, "2026-10-11T15:00:00Z");
        insert(owner, longTitle, longBody, "2026-10-11T15:00:00Z");

        long count = owner.withHandle(handle -> handle.createQuery("SELECT count(*) FROM announcements")
                .mapTo(Long.class)
                .one());
        assertEquals(2L, count);
    }

    @Test
    void 並べ替えの索引は_公開日時と_id_の降順で作られる() {
        String indexDef = PostgresTestSupport.ownerJdbi().withHandle(handle -> handle.createQuery(
                        "SELECT indexdef FROM pg_indexes"
                                + " WHERE schemaname = 'public' AND indexname = 'announcements_published_order_idx'")
                .mapTo(String.class)
                .one());

        assertTrue(indexDef.contains("(published_at DESC, id DESC)"), indexDef);
    }

    @Test
    void サンプルの_SQL_が読み込め_一覧の確認に必要なお知らせを含む() throws Exception {
        Jdbi owner = PostgresTestSupport.ownerJdbi();
        String sql = sampleSql();

        owner.useHandle(handle -> handle.getConnection().createStatement().execute(sql));

        OffsetDateTime displayTime = OffsetDateTime.parse("2026-10-11T15:00:00Z");
        long published = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE published_at <= :displayTime")
                .bind("displayTime", displayTime)
                .mapTo(Long.class)
                .one());
        long upcoming = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE published_at > :displayTime")
                .bind("displayTime", displayTime)
                .mapTo(Long.class)
                .one());
        long boundary = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE published_at IN (:before, :at)")
                .bind("before", OffsetDateTime.parse("2026-10-11T14:59:59Z"))
                .bind("at", displayTime)
                .mapTo(Long.class)
                .one());
        long sameTime = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE published_at = :at")
                .bind("at", OffsetDateTime.parse("2026-10-05T01:00:00Z"))
                .mapTo(Long.class)
                .one());
        long scriptTitle = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE title = :title")
                .bind("title", "<script>alert(1)</script>")
                .mapTo(Long.class)
                .one());
        long blankLine = owner.withHandle(handle -> handle.createQuery(
                        "SELECT count(*) FROM announcements WHERE body LIKE :pattern")
                .bind("pattern", "%\n\n%")
                .mapTo(Long.class)
                .one());

        assertTrue(published >= 25, "公開中のお知らせは 25 件以上: " + published);
        assertTrue(upcoming >= 1, "公開前のお知らせを含む");
        assertEquals(2L, boundary, "公開日の境界の 2 件");
        assertEquals(3L, sameTime, "同じ公開日時の 3 件");
        assertEquals(1L, scriptTitle, "<script> を含むタイトル");
        assertTrue(blankLine >= 1, "改行と空行を含む本文");
    }

    private static AppConfig config() {
        return AppConfig.fromEnv(PostgresTestSupport.env());
    }

    private static String sampleSql() throws IOException {
        try (InputStream in = AnnouncementsMigrationTest.class.getResourceAsStream(SAMPLE_SQL)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void insert(Jdbi jdbi, String publishedAtText) {
        insert(jdbi, "タイトル", "本文", publishedAtText);
    }

    private static void insert(Jdbi jdbi, String title, String body, String publishedAtText) {
        jdbi.useHandle(handle -> handle.createUpdate(INSERT_SQL)
                .bind("title", title)
                .bind("body", body)
                .bind("publishedAtText", publishedAtText)
                .execute());
    }

    /** 操作が失敗し、原因の SQLSTATE が期待どおりであることを確かめる (ほかの理由で失敗していないことを見る) */
    private static void assertSqlState(String expected, Executable executable) {
        RuntimeException e = assertThrows(RuntimeException.class, executable);
        assertEquals(expected, sqlStateOf(e));
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
