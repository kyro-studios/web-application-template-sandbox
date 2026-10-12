package com.example.app.web;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 要求の本文 (JSON のオブジェクト) を読む (アーキテクチャ設計 5.1)。
 *
 * <p>次の場合は、本文が無いもの (空のオブジェクト) として扱う。
 *
 * <ul>
 *   <li>{@code Content-Type} が {@code application/json} でない
 *   <li>本文が JSON として読めない
 *   <li>本文が JSON のオブジェクトでない (配列、文字列など)
 * </ul>
 *
 * <p>値が文字列でない入力 (数値、null、オブジェクトなど) は、{@link #string(String)} では無いものとして扱う。
 *
 * <p>本文が {@link WebServer#MAX_REQUEST_SIZE} を超える場合は、{@link RequestBodyLimit} により 413 で受け付けない。
 */
public final class JsonBody {
    private static final Logger log = LoggerFactory.getLogger(JsonBody.class);
    private static final String CACHE_ATTRIBUTE = JsonBody.class.getName();
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final JsonBody EMPTY = new JsonBody(MAPPER.createObjectNode());

    private final JsonNode object;

    private JsonBody(JsonNode object) {
        this.object = object;
    }

    /** 空の本文。 */
    public static JsonBody empty() {
        return EMPTY;
    }

    /** 要求の本文を読む。同じ要求で何度呼んでもよい。 */
    public static JsonBody read(Context ctx) {
        JsonBody cached = ctx.attribute(CACHE_ATTRIBUTE);
        if (cached != null) {
            return cached;
        }
        JsonBody body = isJson(ctx.contentType()) ? parse(RequestBodyLimit.bytes(ctx)) : EMPTY;
        ctx.attribute(CACHE_ATTRIBUTE, body);
        return body;
    }

    /** バイト列を JSON のオブジェクトとして読む。読めない場合は空の本文を返す。 */
    static JsonBody parse(byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY;
        }
        try {
            JsonNode node = MAPPER.readTree(bytes);
            return node != null && node.isObject() ? new JsonBody(node) : EMPTY;
        } catch (JacksonException e) {
            // 例外のメッセージには入力の一部が含まれうるため、種類だけを書く (アーキテクチャ設計 9.3)
            log.info("要求の本文を JSON として読めないため、空の本文として扱います: {}", e.getClass().getName());
            return EMPTY;
        } catch (IOException e) {
            log.info("要求の本文を読めないため、空の本文として扱います: {}", e.getClass().getName());
            return EMPTY;
        }
    }

    /** 入力の値が文字列なら、その値を返す。入力が無い、または文字列でない場合は空を返す。 */
    public Optional<String> string(String name) {
        JsonNode value = object.get(name);
        return value != null && value.isTextual() ? Optional.of(value.textValue()) : Optional.empty();
    }

    /** その名前の項目があるかを、値の種類によらず返す。 */
    public boolean has(String name) {
        return object.has(name);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        int semicolon = contentType.indexOf(';');
        String mediaType = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return mediaType.strip().toLowerCase(Locale.ROOT).equals(JSON_MEDIA_TYPE);
    }
}
