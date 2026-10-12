package com.example.app.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 例外をエラーの応答 (アーキテクチャ設計 5.2) に変える (8.3)。
 *
 * <ul>
 *   <li>{@link ApiException} は、その番号の応答にする。{@code fieldErrors} は E-01 のときだけ付ける
 *   <li>それ以外の例外は、すべて E-90 にする。ログには例外の種類とスタックトレースを書き、応答には原因を含めない
 * </ul>
 *
 * <p>Javalin の {@code HttpResponseException} (存在しないパスの 404、本文が大きすぎる場合の 413 など) は、Javalin
 * が持つ既定の処理に任せる。
 */
public final class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {}

    /** Javalin に例外の処理を登録する。 */
    public static void register(Javalin app) {
        app.exception(ApiException.class, (e, ctx) -> respond(ctx, e.errorCode(), e.fieldErrors()));
        app.exception(Exception.class, (e, ctx) -> {
            log.error("想定外の例外のため E-90 で応答しました: {}", describe(e));
            respond(ctx, ErrorCode.E_90, List.of());
        });
    }

    private static void respond(Context ctx, ErrorCode code, List<FieldError> fieldErrors) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code.code());
        error.put("message", code.message());
        if (code == ErrorCode.E_01) {
            error.put("fieldErrors", fieldErrors);
        }
        ctx.status(code.httpStatus()).json(Map.of("error", error));
    }

    /**
     * 例外の種類とスタックトレースを、例外のメッセージを除いて文字列にする。
     *
     * <p>例外のメッセージには、入力の一部や SQL の引数が含まれうるため書かない (アーキテクチャ設計 9.3)。
     */
    static String describe(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 20) {
            if (depth > 0) {
                sb.append(System.lineSeparator()).append("Caused by: ");
            }
            sb.append(current.getClass().getName());
            for (StackTraceElement element : current.getStackTrace()) {
                sb.append(System.lineSeparator()).append("\tat ").append(element);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }
}
