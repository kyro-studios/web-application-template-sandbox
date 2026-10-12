package com.example.app.announcement.dto;

import java.util.List;

/**
 * 一覧の応答。0 件の場合は空の一覧になる (お知らせ無しの表示は、フロントエンドが行う)。
 *
 * @param announcements 公開中のお知らせ。新しい順、最大 20 件
 */
public record AnnouncementListResponse(List<AnnouncementItem> announcements) {
}
