package com.example.app.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ApiException} と想定外の例外を、5.2 のエラーの応答に変える (アーキテクチャ設計 8.3)。
 */
public final class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorHandler() {
    }

    public static void register(Javalin app) {
        app.exception(ApiException.class, (e, ctx) -> respond(ctx, e.errorCode(), e.fieldErrors()));
        app.exception(Exception.class, ErrorHandler::handleUnexpected);
    }

    private static void handleUnexpected(Exception e, Context ctx) {
        if (e instanceof HttpResponseException response) {
            // Javalin が返す応答 (本文が大きすぎる場合の 413 など) は、そのままの状態で返す
            ctx.status(response.getStatus()).result(response.getMessage());
            return;
        }
        // 応答には原因を含めない。ログには例外の種類とスタックトレースを書く
        log.error("Unexpected exception: {}", e.getClass().getName(), e);
        respond(ctx, ErrorCode.E90, List.of());
    }

    static void respond(Context ctx, ErrorCode errorCode, List<FieldError> fieldErrors) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", errorCode.code());
        error.put("message", errorCode.message());
        if (errorCode == ErrorCode.E01) {
            error.put("fieldErrors", fieldErrors);
        }
        String body;
        try {
            body = MAPPER.writeValueAsString(Map.of("error", error));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write the error response", e);
        }
        // E-05 にも Retry-After は付けない (再開までの時間の目安を知らせない)
        ctx.status(errorCode.status())
                .contentType("application/json; charset=utf-8")
                .result(body);
    }
}
