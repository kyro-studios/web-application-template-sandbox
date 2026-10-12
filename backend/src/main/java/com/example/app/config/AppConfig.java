package com.example.app.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 環境変数から読むアプリケーションの設定。
 *
 * <p>認証機能の設計書 4 章の環境変数と、お知らせの一覧の設計書 3.4 の {@code APP_DB_CONNECTION_TIMEOUT_MS} を読む。
 * 必須の値が無い、または形式が正しくない場合は {@link IllegalArgumentException} を投げ、起動を中止する。
 * 秘密情報 (パスワード) は {@link #toString()} に含めない。
 */
public record AppConfig(
        int httpsPort,
        String tlsCertPath,
        String tlsKeyPath,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String dbMigrationUser,
        String dbMigrationPassword,
        int dbPoolSize,
        int dbConnectionTimeoutMs,
        Path staticDir) {

    static final int DEFAULT_HTTPS_PORT = 8443;
    static final int DEFAULT_DB_POOL_SIZE = 20;
    static final String DEFAULT_STATIC_DIR = "../frontend/dist";

    /** コネクションを取れる時間の上限 (ミリ秒)。これを上回る値は、この値に下げる (お知らせの一覧の設計 3.4、7.2) */
    static final int DB_CONNECTION_TIMEOUT_LIMIT_MS = 1000;

    private static final List<String> REQUIRED_NAMES = List.of(
            "APP_TLS_CERT_PATH",
            "APP_TLS_KEY_PATH",
            "APP_DB_URL",
            "APP_DB_USER",
            "APP_DB_PASSWORD",
            "APP_DB_MIGRATION_USER",
            "APP_DB_MIGRATION_PASSWORD");

    /**
     * 環境変数の一覧から設定を作る。
     *
     * @param env 環境変数 (通常は {@code System.getenv()})
     * @throws IllegalArgumentException 必須の値が無い、または数値の形式や範囲が正しくない場合
     */
    public static AppConfig fromEnv(Map<String, String> env) {
        List<String> missing = REQUIRED_NAMES.stream()
                .filter(name -> isBlank(env.get(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("必須の環境変数が設定されていません: " + String.join(", ", missing));
        }
        return new AppConfig(
                integer(env, "APP_HTTPS_PORT", DEFAULT_HTTPS_PORT, 1, 65_535),
                env.get("APP_TLS_CERT_PATH"),
                env.get("APP_TLS_KEY_PATH"),
                env.get("APP_DB_URL"),
                env.get("APP_DB_USER"),
                env.get("APP_DB_PASSWORD"),
                env.get("APP_DB_MIGRATION_USER"),
                env.get("APP_DB_MIGRATION_PASSWORD"),
                integer(env, "APP_DB_POOL_SIZE", DEFAULT_DB_POOL_SIZE, 1, Integer.MAX_VALUE),
                Math.min(
                        integer(env, "APP_DB_CONNECTION_TIMEOUT_MS", DB_CONNECTION_TIMEOUT_LIMIT_MS, 1, Integer.MAX_VALUE),
                        DB_CONNECTION_TIMEOUT_LIMIT_MS),
                Path.of(optional(env, "APP_STATIC_DIR", DEFAULT_STATIC_DIR)));
    }

    private static int integer(Map<String, String> env, String name, int defaultValue, int min, int max) {
        String value = env.get(name);
        if (isBlank(value)) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(name, min, max);
        }
        if (parsed < min || parsed > max) {
            throw invalid(name, min, max);
        }
        return parsed;
    }

    private static String optional(Map<String, String> env, String name, String defaultValue) {
        String value = env.get(name);
        return isBlank(value) ? defaultValue : value;
    }

    private static IllegalArgumentException invalid(String name, int min, int max) {
        String range = max == Integer.MAX_VALUE ? min + " 以上" : min + " 以上 " + max + " 以下";
        return new IllegalArgumentException(name + " には " + range + " の整数を指定してください");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 秘密情報を伏せた文字列を返す。ログに書かれても、パスワードが漏れないようにする */
    @Override
    public String toString() {
        return "AppConfig["
                + "httpsPort=" + httpsPort
                + ", tlsCertPath=" + tlsCertPath
                + ", tlsKeyPath=" + tlsKeyPath
                + ", dbUrl=" + dbUrl
                + ", dbUser=" + dbUser
                + ", dbPassword=****"
                + ", dbMigrationUser=" + dbMigrationUser
                + ", dbMigrationPassword=****"
                + ", dbPoolSize=" + dbPoolSize
                + ", dbConnectionTimeoutMs=" + dbConnectionTimeoutMs
                + ", staticDir=" + staticDir
                + "]";
    }
}
