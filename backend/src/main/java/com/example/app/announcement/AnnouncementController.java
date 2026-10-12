package com.example.app.announcement;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /api/announcements} (OP-1)。公開中のお知らせの一覧を JSON で返す。
 *
 * <p>認証を要求せず、クエリ文字列、Cookie、ヘッダー、本文を読まない (設計 4.1)。
 * 取得に失敗した場合は、例外の内容を応答に含めず、E-04 の固定の応答を返す (設計 4.3、8.7)。
 */
public class AnnouncementController {

    static final String PATH = "/api/announcements";

    /** E-04 の文言。フロントエンドの失敗の文言と同じ文字列にする (仕様書 7.1) */
    private static final String FAILURE_MESSAGE = "お知らせを表示できませんでした。時間をおいて、もう一度開いてください";

    private static final String FAILURE_CODE = "E-04";

    private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = Objects.requireNonNull(service);
    }

    /** ルートを登録する。GET だけを定義し、ほかのメソッドは定義しない (設計 4.1) */
    public void register(Javalin app) {
        app.get(PATH, this::list);
    }

    private void list(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        try {
            ctx.json(service.findPublished());
        } catch (Exception e) {
            // 例外の内容は応答に含めない。ログには例外だけを書き、タイトルと本文は扱わない
            log.error("お知らせの一覧を取得できませんでした", e);
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .json(new ErrorResponse(new ErrorBody(FAILURE_CODE, FAILURE_MESSAGE)));
        }
    }

    /** E-04 の応答の本文。{@code {"error":{"code":"E-04","message":"..."}}} の形 */
    record ErrorResponse(ErrorBody error) {
    }

    record ErrorBody(String code, String message) {
    }
}
