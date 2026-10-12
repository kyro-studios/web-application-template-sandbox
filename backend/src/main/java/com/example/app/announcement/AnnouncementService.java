package com.example.app.announcement;

import com.example.app.announcement.dto.AnnouncementItem;
import com.example.app.announcement.dto.AnnouncementListResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 一覧の取得の処理。表示の時点を決め、Repository を呼び、応答の形に変える。
 *
 * <p>表示の時点は、1 回の取得につき {@link Clock#instant()} を 1 回だけ呼んで決める (R-1)。
 * 公開日は、公開日時を日本時間 (Asia/Tokyo) に直した年月日とする。実行環境のタイムゾーンは使わない。
 */
public class AnnouncementService {

    /** 一覧に載せる件数の上限 (仕様書 6.3、設計 8.11) */
    static final int LIMIT = 20;

    private static final ZoneId PUBLIC_ZONE = ZoneId.of("Asia/Tokyo");

    private final AnnouncementRepository repository;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 公開中のお知らせの一覧を返す。
     *
     * @throws RuntimeException 取り出しに失敗した場合 (呼び出し側が、取得の失敗として扱う)
     */
    public AnnouncementListResponse findPublished() {
        Instant displayTime = clock.instant();
        List<AnnouncementItem> items = repository.findPublished(displayTime, LIMIT).stream()
                .map(AnnouncementService::toItem)
                .toList();
        return new AnnouncementListResponse(items);
    }

    private static AnnouncementItem toItem(Announcement announcement) {
        String publishedDate = announcement.publishedAt()
                .atZoneSameInstant(PUBLIC_ZONE)
                .toLocalDate()
                .toString();
        return new AnnouncementItem(announcement.title(), announcement.body(), publishedDate);
    }
}
