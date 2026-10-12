package com.example.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppConfigTest {

    private static final String DB_PASSWORD = "app-user-secret";
    private static final String MIGRATION_PASSWORD = "app-owner-secret";

    private static Map<String, String> requiredOnly() {
        Map<String, String> env = new HashMap<>();
        env.put("APP_TLS_CERT_PATH", "certs/localhost.pem");
        env.put("APP_TLS_KEY_PATH", "certs/localhost-key.pem");
        env.put("APP_DB_URL", "jdbc:postgresql://localhost:5432/app");
        env.put("APP_DB_USER", "app_user");
        env.put("APP_DB_PASSWORD", DB_PASSWORD);
        env.put("APP_DB_MIGRATION_USER", "app_owner");
        env.put("APP_DB_MIGRATION_PASSWORD", MIGRATION_PASSWORD);
        return env;
    }

    @Test
    void 必須の値だけを指定すると既定値を使う() {
        AppConfig config = AppConfig.fromEnv(requiredOnly());

        assertEquals(8443, config.httpsPort());
        assertEquals("certs/localhost.pem", config.tlsCertPath());
        assertEquals("certs/localhost-key.pem", config.tlsKeyPath());
        assertEquals("jdbc:postgresql://localhost:5432/app", config.dbUrl());
        assertEquals("app_user", config.dbUser());
        assertEquals(DB_PASSWORD, config.dbPassword());
        assertEquals("app_owner", config.dbMigrationUser());
        assertEquals(MIGRATION_PASSWORD, config.dbMigrationPassword());
        assertEquals(20, config.dbPoolSize());
        assertEquals(1000, config.dbConnectionTimeoutMs());
        assertEquals(Path.of("../frontend/dist"), config.staticDir());
    }

    @Test
    void 任意の値を指定するとその値を使う() {
        Map<String, String> env = requiredOnly();
        env.put("APP_HTTPS_PORT", "9443");
        env.put("APP_DB_POOL_SIZE", "5");
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", "500");
        env.put("APP_STATIC_DIR", "/srv/app/dist");

        AppConfig config = AppConfig.fromEnv(env);

        assertEquals(9443, config.httpsPort());
        assertEquals(5, config.dbPoolSize());
        assertEquals(500, config.dbConnectionTimeoutMs());
        assertEquals(Path.of("/srv/app/dist"), config.staticDir());
    }

    @Test
    void 接続の時間の上限が1000ミリ秒を超える場合は1000ミリ秒に下げる() {
        Map<String, String> env = requiredOnly();
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", "5000");

        assertEquals(1000, AppConfig.fromEnv(env).dbConnectionTimeoutMs());
    }

    @Test
    void 空白だけの任意の値は指定が無いものとして既定値を使う() {
        Map<String, String> env = requiredOnly();
        env.put("APP_HTTPS_PORT", " ");
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", "");

        AppConfig config = AppConfig.fromEnv(env);

        assertEquals(8443, config.httpsPort());
        assertEquals(1000, config.dbConnectionTimeoutMs());
    }

    @ParameterizedTest
    @CsvSource({
            "APP_TLS_CERT_PATH",
            "APP_TLS_KEY_PATH",
            "APP_DB_URL",
            "APP_DB_USER",
            "APP_DB_PASSWORD",
            "APP_DB_MIGRATION_USER",
            "APP_DB_MIGRATION_PASSWORD",
    })
    void 必須の値が無い場合は例外になり_名前を示す(String name) {
        Map<String, String> env = requiredOnly();
        env.remove(name);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(env));

        assertTrue(e.getMessage().contains(name), e.getMessage());
    }

    @Test
    void 必須の値が空白だけの場合も無いものとして扱う() {
        Map<String, String> env = requiredOnly();
        env.put("APP_TLS_KEY_PATH", "   ");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(env));

        assertTrue(e.getMessage().contains("APP_TLS_KEY_PATH"), e.getMessage());
    }

    @Test
    void 必須の値が複数無い場合は_すべての名前を示す() {
        Map<String, String> env = requiredOnly();
        env.remove("APP_TLS_CERT_PATH");
        env.remove("APP_DB_URL");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(env));

        assertTrue(e.getMessage().contains("APP_TLS_CERT_PATH"), e.getMessage());
        assertTrue(e.getMessage().contains("APP_DB_URL"), e.getMessage());
    }

    @Test
    void 必須の値が無い場合の例外に_値の内容を含めない() {
        Map<String, String> env = requiredOnly();
        env.remove("APP_DB_MIGRATION_USER");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(env));

        assertFalse(e.getMessage().contains(DB_PASSWORD), e.getMessage());
        assertFalse(e.getMessage().contains(MIGRATION_PASSWORD), e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "APP_HTTPS_PORT, abc",
            "APP_HTTPS_PORT, 0",
            "APP_HTTPS_PORT, 65536",
            "APP_DB_POOL_SIZE, 0",
            "APP_DB_POOL_SIZE, 2.5",
            "APP_DB_CONNECTION_TIMEOUT_MS, 0",
            "APP_DB_CONNECTION_TIMEOUT_MS, -1",
    })
    void 数値の形式や範囲が正しくない場合は例外になる(String name, String value) {
        Map<String, String> env = requiredOnly();
        env.put(name, value);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnv(env));

        assertTrue(e.getMessage().contains(name), e.getMessage());
    }

    @Test
    void toStringはパスワードを含まない() {
        String text = AppConfig.fromEnv(requiredOnly()).toString();

        assertFalse(text.contains(DB_PASSWORD), text);
        assertFalse(text.contains(MIGRATION_PASSWORD), text);
        assertTrue(text.contains("dbUser=app_user"), text);
    }
}
