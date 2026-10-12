package com.example.app.web;

import io.javalin.http.ContentTooLargeResponse;
import io.javalin.http.Context;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 要求の本文の大きさを {@link WebServer#MAX_REQUEST_SIZE} までに制限する。
 *
 * <p>{@code http.maxRequestSize} は {@code Content-Length} だけを見るため、{@code Content-Length} の無い (chunked の)
 * 要求には効かない。そこで、{@link WebServer} はすべての要求で、{@code Content-Type} やルートによらず {@link #enforce(Context)}
 * を呼び、上限を超えて読まずに 413 で受け付けない。読んだ本文は要求の属性に残し、{@link #bytes(Context)} で返す。
 */
public final class RequestBodyLimit {
    private static final String CACHE_ATTRIBUTE = RequestBodyLimit.class.getName();

    private RequestBodyLimit() {}

    /** 本文が上限を超える場合は、413 で受け付けない。 */
    public static void enforce(Context ctx) {
        bytes(ctx);
    }

    /** 上限以内の本文を返す。同じ要求で何度呼んでもよい。上限を超える場合は、413 で受け付けない。 */
    public static byte[] bytes(Context ctx) {
        byte[] cached = ctx.attribute(CACHE_ATTRIBUTE);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = readBytes(ctx);
        ctx.attribute(CACHE_ATTRIBUTE, bytes);
        return bytes;
    }

    private static byte[] readBytes(Context ctx) {
        long max = WebServer.MAX_REQUEST_SIZE;
        if (ctx.req().getContentLengthLong() > max) {
            throw new ContentTooLargeResponse();
        }
        // Content-Length が無い (chunked の) 要求でも、上限を超えて読まない
        try (InputStream in = ctx.bodyInputStream()) {
            byte[] bytes = in.readNBytes(Math.toIntExact(max + 1));
            if (bytes.length > max) {
                throw new ContentTooLargeResponse();
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
