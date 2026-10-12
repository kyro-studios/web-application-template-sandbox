package com.example.app.announcement;

import java.time.OffsetDateTime;

/**
 * データベースから読んだお知らせ 1 件。
 *
 * <p>タイトルと本文は、登録されたとおりの値を持つ (加工しない)。公開日時は、タイムゾーンを含む日時として持つ。
 * 登録の順序 ({@code id}) は、並べ替えにだけ使い、この値には含めない。
 */
public record Announcement(String title, String body, OffsetDateTime publishedAt) {
}
