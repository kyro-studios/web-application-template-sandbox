package com.example.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppConfigTest {

    private static final List<String> REQUIRED = List.of(
            "APP_TLS_CERT_PATH",
            "APP_TLS_KEY_PATH",
            "APP_DB_URL",
            "APP_DB_USER",
            "APP_DB_PASSWORD",
            "APP_DB_MIGRATION_USER",
            "APP_DB_MIGRATION_PASSWORD");

    private static Map<String, String> requiredOnly() {
        Map<String, String> env = new HashMap<>();
        env.put("APP_TLS_CERT_PATH", "certs/localhost.pem");
        env.put("APP_TLS_KEY_PATH", "certs/localhost-key.pem");
        env.put("APP_DB_URL", "jdbc:postgresql://localhost:5432/app");
        env.put("APP_DB_USER", "app_user");
        env.put("APP_DB_PASSWORD", "app-user-secret");
        env.put("APP_DB_MIGRATION_USER", "app_owner");
        env.put("APP_DB_MIGRATION_PASSWORD", "app-owner-secret");
        return env;
    }

    @Test
    void 必須の値だけで読め_任意の値は既定値になる() {
        AppConfig config = AppConfig.fromEnvironment(requiredOnly());

        assertEquals(8443, config.httpsPort());
        assertEquals(Path.of("certs/localhost.pem"), config.tlsCertPath());
        assertEquals(Path.of("certs/localhost-key.pem"), config.tlsKeyPath());
        assertEquals("jdbc:postgresql://localhost:5432/app", config.dbUrl());
        assertEquals("app_user", config.dbUser());
        assertEquals("app-user-secret", config.dbPassword());
        assertEquals("app_owner", config.dbMigrationUser());
        assertEquals("app-owner-secret", config.dbMigrationPassword());
        assertEquals(20, config.dbPoolSize());
        assertEquals(Path.of("../frontend/dist"), config.staticDir());
    }

    @Test
    void 任意の値を設定できる() {
        Map<String, String> env = requiredOnly();
        env.put("APP_HTTPS_PORT", "9443");
        env.put("APP_DB_POOL_SIZE", "5");
        env.put("APP_STATIC_DIR", "/srv/app/static");

        AppConfig config = AppConfig.fromEnvironment(env);

        assertEquals(9443, config.httpsPort());
        assertEquals(5, config.dbPoolSize());
        assertEquals(Path.of("/srv/app/static"), config.staticDir());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "APP_TLS_CERT_PATH",
        "APP_TLS_KEY_PATH",
        "APP_DB_URL",
        "APP_DB_USER",
        "APP_DB_PASSWORD",
        "APP_DB_MIGRATION_USER",
        "APP_DB_MIGRATION_PASSWORD"
    })
    void 必須の値が無ければ起動を中止する(String name) {
        Map<String, String> env = requiredOnly();
        env.remove(name);

        AppConfig.ConfigException e =
                assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
        assertTrue(e.getMessage().contains(name), e.getMessage());
    }

    @Test
    void 空の値は設定されていないものとして扱う() {
        Map<String, String> env = requiredOnly();
        env.put("APP_DB_URL", "  ");

        assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
    }

    @Test
    void 何も設定されていなければ_必須の値をすべて知らせる() {
        AppConfig.ConfigException e =
                assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(Map.of()));
        for (String name : REQUIRED) {
            assertTrue(e.getMessage().contains(name), e.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"APP_HTTPS_PORT=abc", "APP_HTTPS_PORT=65536", "APP_HTTPS_PORT=-1", "APP_DB_POOL_SIZE=0"})
    void 数値の形が誤っていれば起動を中止する(String setting) {
        String[] pair = setting.split("=", 2);
        Map<String, String> env = requiredOnly();
        env.put(pair[0], pair[1]);

        AppConfig.ConfigException e =
                assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
        assertTrue(e.getMessage().contains(pair[0]), e.getMessage());
    }

    @Test
    void 文字列にしてもパスワードを含めない() {
        String text = AppConfig.fromEnvironment(requiredOnly()).toString();

        assertFalse(text.contains("app-user-secret"), text);
        assertFalse(text.contains("app-owner-secret"), text);
    }
}
