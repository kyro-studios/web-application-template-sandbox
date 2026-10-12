package com.example.app.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 環境変数から読んだ設定 (アーキテクチャ設計 4 章)。
 *
 * <p>必須の値が無い、または値の形が誤っている場合は {@link ConfigException} を投げ、起動を中止させる。
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

    /** 環境変数を読む。 */
    public static AppConfig fromEnvironment(Map<String, String> env) {
        Reader reader = new Reader(env);
        int httpsPort = reader.optionalInt("APP_HTTPS_PORT", DEFAULT_HTTPS_PORT, 0, 65535);
        String tlsCertPath = reader.required("APP_TLS_CERT_PATH");
        String tlsKeyPath = reader.required("APP_TLS_KEY_PATH");
        String dbUrl = reader.required("APP_DB_URL");
        String dbUser = reader.required("APP_DB_USER");
        String dbPassword = reader.required("APP_DB_PASSWORD");
        String dbMigrationUser = reader.required("APP_DB_MIGRATION_USER");
        String dbMigrationPassword = reader.required("APP_DB_MIGRATION_PASSWORD");
        int dbPoolSize = reader.optionalInt("APP_DB_POOL_SIZE", DEFAULT_DB_POOL_SIZE, 1, Integer.MAX_VALUE);
        String staticDir = reader.optional("APP_STATIC_DIR", DEFAULT_STATIC_DIR);

        if (!reader.problems.isEmpty()) {
            throw new ConfigException("設定に誤りがあるため起動を中止します: " + String.join("、", reader.problems));
        }
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

    /** パスワードをログなどに出さないように、値を伏せる。 */
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

    /** 設定の誤り。メッセージには環境変数の名前だけを含め、値は含めない。 */
    public static final class ConfigException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ConfigException(String message) {
            super(message);
        }
    }

    private static final class Reader {
        private final Map<String, String> env;
        private final List<String> problems = new ArrayList<>();

        Reader(Map<String, String> env) {
            this.env = env;
        }

        String required(String name) {
            String value = value(name);
            if (value == null) {
                problems.add(name + " が設定されていません");
            }
            return value;
        }

        String optional(String name, String defaultValue) {
            String value = value(name);
            return value == null ? defaultValue : value;
        }

        int optionalInt(String name, int defaultValue, int min, int max) {
            String value = value(name);
            if (value == null) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= min && parsed <= max) {
                    return parsed;
                }
            } catch (NumberFormatException e) {
                // 下で誤りとして扱う
            }
            problems.add(name + " は " + min + " ～ " + max + " の整数で設定してください");
            return defaultValue;
        }

        /** 空の値 (空白だけのものを含む) は、設定されていないものとして扱う。 */
        private String value(String name) {
            String value = env.get(name);
            return value == null || value.isBlank() ? null : value;
        }
    }
}
