package com.example.app.announcement.dto;

/**
 * 応答に含めるお知らせ 1 件。
 *
 * @param title タイトル。登録されたとおりの値
 * @param body 本文。登録されたとおりの値 (改行を含む)
 * @param publishedDate 公開日 (日本時間の年月日。{@code 2026-10-12} の形)
 */
public record AnnouncementItem(String title, String body, String publishedDate) {
}
