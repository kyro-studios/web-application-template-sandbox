package com.example.app.web;

import io.javalin.http.Context;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * アプリケーションのログに、要求の概要を書く (アーキテクチャ設計 9.3)。
 *
 * <p>書くのは、メソッド、パスのテンプレート、応答の状態、処理時間だけとする。要求と応答の本文、ヘッダー (Cookie、
 * Set-Cookie を含む)、クエリ文字列、実際のパスは書かない。
 */
public final class RequestLogger {
    private static final Logger log = LoggerFactory.getLogger(RequestLogger.class);
    private static final String START_ATTRIBUTE = RequestLogger.class.getName() + ".start";

    /** 対応するルートが無い要求 (静的ファイル、404 など) で、パスの代わりに書く値。 */
    static final String NO_ROUTE = "(no route)";

    private final Clock clock;

    public RequestLogger(Clock clock) {
        this.clock = clock;
    }

    /** 要求の処理を始めたときに呼ぶ。 */
    public void start(Context ctx) {
        ctx.attribute(START_ATTRIBUTE, clock.instant());
    }

    /** 応答を書いた後に呼ぶ。 */
    public void log(Context ctx) {
        Instant start = ctx.attribute(START_ATTRIBUTE);
        long elapsedMillis = start == null ? 0 : Duration.between(start, clock.instant()).toMillis();
        log.info("{} {} {} {}ms", ctx.method(), pathTemplate(ctx), ctx.statusCode(), elapsedMillis);
    }

    private static String pathTemplate(Context ctx) {
        String path;
        try {
            path = ctx.endpointHandlerPath();
        } catch (IllegalStateException e) {
            // before ハンドラーの後にルートを通らずに終わった要求 (静的ファイルなど)
            return NO_ROUTE;
        }
        // ルートが無い場合、Javalin は "/" で始まらない値 (空、"*"、説明の文字列) を返す
        return path.startsWith("/") ? path : NO_ROUTE;
    }
}
