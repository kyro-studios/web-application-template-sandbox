package com.example.app.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 要求の本文を読む (アーキテクチャ設計 5.1)。
 *
 * <p>{@code Content-Type: application/json} でない、JSON として読めない、JSON のオブジェクトでない場合は、
 * 空のオブジェクトとして扱う。値が文字列でない入力は、無いものとして扱う。
 */
public final class JsonBody {

    private static final Logger log = LoggerFactory.getLogger(JsonBody.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final JsonBody EMPTY = new JsonBody(Map.of(), Set.of());

    private final Map<String, String> strings;
    private final Set<String> names;

    private JsonBody(Map<String, String> strings, Set<String> names) {
        this.strings = strings;
        this.names = names;
    }

    public static JsonBody read(Context ctx) {
        if (!isJson(ctx.contentType())) {
            return EMPTY;
        }
        // 本文の大きさの上限 (http.maxRequestSize) を超えると、Javalin が 413 の例外を投げる
        byte[] body = ctx.bodyAsBytes();
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException e) {
            // Jackson の例外のメッセージには入力の一部が含まれうるため、種類だけを書く (9.3)
            log.info("Request body is not valid JSON: {}", e.getClass().getName());
            return EMPTY;
        }
        if (root == null || !root.isObject()) {
            return EMPTY;
        }
        Map<String, String> strings = new HashMap<>();
        Set<String> names = new HashSet<>();
        root.fields().forEachRemaining(field -> {
            names.add(field.getKey());
            if (field.getValue().isTextual()) {
                strings.put(field.getKey(), field.getValue().textValue());
            }
        });
        return new JsonBody(Map.copyOf(strings), Set.copyOf(names));
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        int separator = contentType.indexOf(';');
        String mediaType = separator < 0 ? contentType : contentType.substring(0, separator);
        return mediaType.strip().toLowerCase(Locale.ROOT).equals("application/json");
    }

    /** 文字列の値を返す。入力が無い、または値が文字列でない場合は空。 */
    public Optional<String> string(String name) {
        return Optional.ofNullable(strings.get(name));
    }

    /** 値の種類によらず、その名前の項目が本文にあるか (5.6.8 の {@code accountId} の判断のため)。 */
    public boolean has(String name) {
        return names.contains(name);
    }
}
