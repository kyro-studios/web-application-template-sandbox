package com.example.app.web;

/**
 * 入力ごとの誤り。入力の名前と、知らせる内容を持つ (アーキテクチャ設計 5.3)。
 */
public record FieldError(String field, String message) {
}
