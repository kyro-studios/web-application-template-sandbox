package com.example.app.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.announcement.dto.AnnouncementItem;
import com.example.app.announcement.dto.AnnouncementListResponse;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnnouncementServiceTest {

    private static final Instant DISPLAY_TIME = Instant.parse("2026-10-11T15:00:00Z");

    /** Repository はスタブに置き換えるため、この Jdbi では接続しない */
    private static final Jdbi UNUSED_JDBI = Jdbi.create(() -> {
        throw new SQLException("試験では接続しない");
    });

    @Test
    void 表示の時点は_1_回の取得で_1_度だけ決めて_Repository_に渡す() {
        CountingClock clock = new CountingClock(DISPLAY_TIME);
        StubRepository repository = new StubRepository(List.of());

        new AnnouncementService(repository, clock).findPublished();

        assertEquals(1, clock.calls);
        assertEquals(1, repository.calls);
        assertEquals(DISPLAY_TIME, repository.displayTime);
    }

    @Test
    void 件数の上限は_20_で_Repository_に渡す() {
        StubRepository repository = new StubRepository(List.of());

        new AnnouncementService(repository, new CountingClock(DISPLAY_TIME)).findPublished();

        assertEquals(20, AnnouncementService.LIMIT);
        assertEquals(20, repository.limit);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-11T14:59:59Z, 2026-10-11",
            "2026-10-11T15:00:00Z, 2026-10-12",
            "2026-01-05T00:00:00+09:00, 2026-01-05",
            "2026-10-05T09:00:00+09:00, 2026-10-05",
            "2026-10-12T00:00:00+09:00, 2026-10-12",
    })
    void 公開日は_日本時間の年月日を_0_埋めせずに_YYYY_MM_DD_で返す(String publishedAt, String expectedDate) {
        StubRepository repository = new StubRepository(List.of(
                new Announcement("タイトル", "本文", OffsetDateTime.parse(publishedAt))));

        AnnouncementItem item = new AnnouncementService(repository, new CountingClock(DISPLAY_TIME))
                .findPublished()
                .announcements()
                .get(0);

        assertEquals(expectedDate, item.publishedDate());
    }

    @Test
    void 公開日は_実行環境のタイムゾーンに依らない() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        try {
            StubRepository repository = new StubRepository(List.of(
                    new Announcement("タイトル", "本文", OffsetDateTime.parse("2026-10-11T15:00:00Z"))));

            AnnouncementItem item = new AnnouncementService(repository, new CountingClock(DISPLAY_TIME))
                    .findPublished()
                    .announcements()
                    .get(0);

            assertEquals("2026-10-12", item.publishedDate());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void タイトルと本文は_前後の空白と改行を含めて_加工せずに返す() {
        String title = "  前後に空白  ";
        String body = "\n1 行目\n\n  2 行目 <script>alert(1)</script>\r\n";
        StubRepository repository = new StubRepository(List.of(
                new Announcement(title, body, OffsetDateTime.parse("2026-10-11T14:00:00Z"))));

        AnnouncementItem item = new AnnouncementService(repository, new CountingClock(DISPLAY_TIME))
                .findPublished()
                .announcements()
                .get(0);

        assertEquals(title, item.title());
        assertEquals(body, item.body());
    }

    @Test
    void 応答は_Repository_が返した順序を保ち_件数も変えない() {
        StubRepository repository = new StubRepository(List.of(
                new Announcement("新しい", "本文", OffsetDateTime.parse("2026-10-11T14:00:00Z")),
                new Announcement("同じ", "本文", OffsetDateTime.parse("2026-10-11T13:00:00Z")),
                new Announcement("同じ", "本文", OffsetDateTime.parse("2026-10-11T12:00:00Z"))));

        AnnouncementListResponse response = new AnnouncementService(repository, new CountingClock(DISPLAY_TIME)).findPublished();

        assertEquals(
                List.of("新しい", "同じ", "同じ"),
                response.announcements().stream().map(AnnouncementItem::title).toList());
    }

    @Test
    void 取り出したお知らせが_0_件なら_空の一覧を返す() {
        AnnouncementListResponse response = new AnnouncementService(new StubRepository(List.of()), new CountingClock(DISPLAY_TIME))
                .findPublished();

        assertTrue(response.announcements().isEmpty());
    }

    @Test
    void 取り出しに失敗した例外は_そのまま呼び出し側へ伝える() {
        AnnouncementRepository failing = new AnnouncementRepository(UNUSED_JDBI) {
            @Override
            public List<Announcement> findPublished(Instant displayTime, int limit) {
                throw new IllegalStateException("接続できない");
            }
        };

        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> new AnnouncementService(failing, new CountingClock(DISPLAY_TIME)).findPublished());

        assertEquals("接続できない", e.getMessage());
    }

    /** 固定の時刻を返し、呼ばれた回数を数える Clock */
    private static final class CountingClock extends Clock {

        private final Instant instant;
        private int calls;

        CountingClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            calls++;
            return instant;
        }
    }

    /** 渡された引数を記録し、用意した一覧を返すスタブ。Jdbi には接続しない */
    private static final class StubRepository extends AnnouncementRepository {

        private final List<Announcement> announcements;
        private int calls;
        private Instant displayTime;
        private int limit;

        StubRepository(List<Announcement> announcements) {
            super(UNUSED_JDBI);
            this.announcements = announcements;
        }

        @Override
        public List<Announcement> findPublished(Instant displayTime, int limit) {
            calls++;
            this.displayTime = displayTime;
            this.limit = limit;
            return announcements;
        }
    }
}
