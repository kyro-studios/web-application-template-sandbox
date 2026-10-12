package com.example.app.config;

import java.util.Map;

/**
 * 環境変数から読むバックエンドの設定 (アーキテクチャ設計 3.4、認証機能の設計 4 章)。
 *
 * <p>必須の値が無い場合は、起動を中止する。例外のメッセージには、環境変数の名前だけを含め、値は含めない。
 *
 * @param httpsPort HTTPS で待ち受けるポート
 * @param tlsCertPath サーバー証明書 (PEM) のパス
 * @param tlsKeyPath 秘密鍵 (PEM) のパス
 * @param dbUrl JDBC の URL
 * @param dbUser アプリケーション用の利用者の名前 (SELECT などの DML だけを持つ)
 * @param dbPassword アプリケーション用の利用者のパスワード
 * @param migrationUser マイグレーション用の利用者の名前 (所有者)
 * @param migrationPassword マイグレーション用の利用者のパスワード
 * @param poolSize コネクションプールの最大の数
 * @param dbConnectionTimeoutMs コネクションを取れる時間の上限 (ミリ秒)。1,000 ミリ秒を超える値は 1,000 ミリ秒に丸める
 */
public record AppConfig(
        int httpsPort,
        String tlsCertPath,
        String tlsKeyPath,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String migrationUser,
        String migrationPassword,
        int poolSize,
        long dbConnectionTimeoutMs) {

    /** コネクションを取れる時間の上限 (ミリ秒)。お知らせの取得の上限時間 (設計 7.2) を超えないための値 */
    static final long MAX_DB_CONNECTION_TIMEOUT_MS = 1000;

    public static AppConfig fromEnv(Map<String, String> env) {
        return new AppConfig(
                intOrDefault(env, "APP_HTTPS_PORT", 8443),
                required(env, "APP_TLS_CERT_PATH"),
                required(env, "APP_TLS_KEY_PATH"),
                required(env, "APP_DB_URL"),
                required(env, "APP_DB_USER"),
                required(env, "APP_DB_PASSWORD"),
                required(env, "APP_DB_MIGRATION_USER"),
                required(env, "APP_DB_MIGRATION_PASSWORD"),
                intOrDefault(env, "APP_DB_POOL_SIZE", 20),
                Math.min(longOrDefault(env, "APP_DB_CONNECTION_TIMEOUT_MS", MAX_DB_CONNECTION_TIMEOUT_MS),
                        MAX_DB_CONNECTION_TIMEOUT_MS));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("必須の環境変数が設定されていません: " + name);
        }
        return value;
    }

    private static int intOrDefault(Map<String, String> env, String name, int defaultValue) {
        return (int) longOrDefault(env, name, defaultValue);
    }

    private static long longOrDefault(Map<String, String> env, String name, long defaultValue) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("環境変数の値が数値ではありません: " + name, e);
        }
    }
}
