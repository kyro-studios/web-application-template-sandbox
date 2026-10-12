package com.example.app.web;

/**
 * 異常の番号と、HTTP の状態、利用者に知らせる内容 (アーキテクチャ設計 5.2)。
 */
public enum ErrorCode {
    E01("E-01", 400, "入力に誤りがあります"),
    E02("E-02", 400, "パスワードが正しくありません"),
    E03("E-03", 409, "このメールアドレスは使えません"),
    E04("E-04", 401, "メールアドレスまたはパスワードが正しくありません"),
    E05("E-05", 429, "続けてログインに失敗したため、しばらくログインできません"),
    E06("E-06", 401, "ログインが必要です"),
    E07("E-07", 409, "既にログインしています"),
    E08("E-08", 403, "その操作は行えません"),
    E90("E-90", 503, "処理できませんでした。時間をおいてやり直してください");

    private final String code;
    private final int status;
    private final String message;

    ErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    /** 応答の {@code code} に入れる番号 (例: {@code E-01})。 */
    public String code() {
        return code;
    }

    /** HTTP の状態。 */
    public int status() {
        return status;
    }

    /** 応答の {@code message} に入れる内容。 */
    public String message() {
        return message;
    }
}
