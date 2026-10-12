package com.example.app.web;

/** 異常の番号と、HTTP の状態、利用者に知らせる内容 (アーキテクチャ設計 5.2)。 */
public enum ErrorCode {
    E_01("E-01", 400, "入力に誤りがあります"),
    E_02("E-02", 400, "パスワードが正しくありません"),
    E_03("E-03", 409, "このメールアドレスは使えません"),
    E_04("E-04", 401, "メールアドレスまたはパスワードが正しくありません"),
    E_05("E-05", 429, "続けてログインに失敗したため、しばらくログインできません"),
    E_06("E-06", 401, "ログインが必要です"),
    E_07("E-07", 409, "既にログインしています"),
    E_08("E-08", 403, "その操作は行えません"),
    E_90("E-90", 503, "処理できませんでした。時間をおいてやり直してください");

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /** 応答の {@code code} に入れる番号 (例: {@code E-01})。 */
    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** 応答の {@code message} に入れる内容。 */
    public String message() {
        return message;
    }
}
