package com.example.app.web;

import io.javalin.http.Context;
import java.util.Map;

/** すべての応答に付ける共通のヘッダー (アーキテクチャ設計 5.1、9.1)。 */
public final class SecurityHeaders {

    /** 共通のヘッダーの名前と値。 */
    public static final Map<String, String> HEADERS = Map.of(
            "Cache-Control", "no-store",
            "Strict-Transport-Security", "max-age=31536000");

    private SecurityHeaders() {}

    /** 応答にヘッダーを付ける。 */
    public static void apply(Context ctx) {
        HEADERS.forEach(ctx::header);
    }
}
