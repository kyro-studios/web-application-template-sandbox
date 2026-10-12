package com.example.app.web;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * アプリケーションのログに要求の概要を書く (アーキテクチャ設計 9.3)。
 *
 * <p>メソッド、パスのテンプレート、応答の状態、処理時間だけを書く。本文、ヘッダー、クエリ文字列は書かない。
 */
public final class RequestLogger {

    private static final Logger log = LoggerFactory.getLogger(RequestLogger.class);

    public void log(Context ctx, float executionTimeMs) {
        log.info("{} {} {} {}ms", ctx.method().name(), pathTemplate(ctx), ctx.statusCode(), Math.round(executionTimeMs));
    }

    /**
     * 一致したルートのパスのテンプレート (例: {@code /api/me/profile})。実際のパスには利用者の入力が
     * 含まれうるため書かない。ルートに一致しなかった要求 (静的ファイルなど) は、種類だけを書く。
     */
    static String pathTemplate(Context ctx) {
        String matched = ctx.matchedPath();
        if (matched != null && !matched.isEmpty() && !matched.equals("*")) {
            return matched;
        }
        return WebServer.isApiPath(ctx.path()) ? "(no route)" : "(static)";
    }
}
