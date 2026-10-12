package com.example.app.announcement;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;

/**
 * お知らせを読み出す。読み取りだけを行い、書き込みのメソッドは作らない (R-6)。
 *
 * <p>公開中かどうかの判断は、SQL の {@code WHERE} で、呼び出し側が渡す表示の時点 (Instant) と比べて行う。
 * SQL の {@code now()} は使わない。
 */
public class AnnouncementRepository {

    /** 問い合わせの時間切れ (秒)。これを超えると例外になり、一覧の取得は失敗になる (設計 7.2) */
    static final int QUERY_TIMEOUT_SECONDS = 2;

    private static final String FIND_PUBLISHED_SQL = """
            SELECT title, body, published_at
            FROM announcements
            WHERE published_at <= :displayTime
            ORDER BY published_at DESC, id DESC
            LIMIT :limit
            """;

    private final Jdbi jdbi;

    public AnnouncementRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi);
    }

    /**
     * 表示の時点までに公開されたお知らせを、公開日時の新しい順 (同じ時点なら登録の順序が後のもの) に、最大 {@code limit} 件返す。
     *
     * @param displayTime 表示の時点。公開日時がこれと同じ時点のお知らせも含める
     * @param limit 返す件数の上限
     */
    public List<Announcement> findPublished(Instant displayTime, int limit) {
        return jdbi.withHandle(handle -> handle.createQuery(FIND_PUBLISHED_SQL)
                .setQueryTimeout(QUERY_TIMEOUT_SECONDS)
                .bind("displayTime", displayTime.atOffset(ZoneOffset.UTC))
                .bind("limit", limit)
                .map((rs, ctx) -> new Announcement(
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getObject("published_at", OffsetDateTime.class)))
                .list());
    }
}
