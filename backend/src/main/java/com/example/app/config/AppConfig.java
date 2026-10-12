package com.example.app.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 環境変数から読んだ設定 (アーキテクチャ設計 4 章)。
 */
public record AppConfig(
        int httpsPort,
        Path tlsCertPath,
        Path tlsKeyPath,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String dbMigrationUser,
        String dbMigrationPassword,
        int dbPoolSize,
        Path staticDir) {

    public static final int DEFAULT_HTTPS_PORT = 8443;
    public static final int DEFAULT_DB_POOL_SIZE = 20;
    public static final String DEFAULT_STATIC_DIR = "../frontend/dist";

    /**
     * 環境変数から設定を読む。必須の値が無い場合や、値が正しくない場合は {@link ConfigException} を投げる。
     */
    public static AppConfig fromEnvironment(Map<String, String> env) {
        List<String> missing = new ArrayList<>();
        String tlsCertPath = required(env, "APP_TLS_CERT_PATH", missing);
        String tlsKeyPath = required(env, "APP_TLS_KEY_PATH", missing);
        String dbUrl = required(env, "APP_DB_URL", missing);
        String dbUser = required(env, "APP_DB_USER", missing);
        String dbPassword = required(env, "APP_DB_PASSWORD", missing);
        String dbMigrationUser = required(env, "APP_DB_MIGRATION_USER", missing);
        String dbMigrationPassword = required(env, "APP_DB_MIGRATION_PASSWORD", missing);
        if (!missing.isEmpty()) {
            throw new ConfigException("Required environment variables are not set: " + String.join(", ", missing));
        }

        int httpsPort = integer(env, "APP_HTTPS_PORT", DEFAULT_HTTPS_PORT, 1, 65535);
        int dbPoolSize = integer(env, "APP_DB_POOL_SIZE", DEFAULT_DB_POOL_SIZE, 1, Integer.MAX_VALUE);
        String staticDir = optional(env, "APP_STATIC_DIR", DEFAULT_STATIC_DIR);

        return new AppConfig(
                httpsPort,
                Path.of(tlsCertPath),
                Path.of(tlsKeyPath),
                dbUrl,
                dbUser,
                dbPassword,
                dbMigrationUser,
                dbMigrationPassword,
                dbPoolSize,
                Path.of(staticDir));
    }

    private static String required(Map<String, String> env, String name, List<String> missing) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
            return null;
        }
        return value;
    }

    private static String optional(Map<String, String> env, String name, String defaultValue) {
        String value = env.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int integer(Map<String, String> env, String name, int defaultValue, int min, int max) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new ConfigException(name + " must be an integer");
        }
        if (parsed < min || parsed > max) {
            throw new ConfigException(name + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    /** パスワードをログに出さないように、toString() では値を伏せる。 */
    @Override
    public String toString() {
        return "AppConfig[httpsPort=" + httpsPort
                + ", tlsCertPath=" + tlsCertPath
                + ", tlsKeyPath=" + tlsKeyPath
                + ", dbUrl=" + dbUrl
                + ", dbUser=" + dbUser
                + ", dbPassword=********"
                + ", dbMigrationUser=" + dbMigrationUser
                + ", dbMigrationPassword=********"
                + ", dbPoolSize=" + dbPoolSize
                + ", staticDir=" + staticDir + "]";
    }

    /** 設定が足りない、または正しくないため、起動を中止することを表す。 */
    public static final class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }
    }
}
