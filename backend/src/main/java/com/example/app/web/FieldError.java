package com.example.app.web;

import java.util.Objects;

/** 入力ごとの誤り。入力の名前と、知らせる内容を持つ。入力の値そのものは持たない (アーキテクチャ設計 5.2)。 */
public record FieldError(String field, String message) {

    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
