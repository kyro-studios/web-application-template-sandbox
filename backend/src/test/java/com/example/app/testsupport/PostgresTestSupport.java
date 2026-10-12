package com.example.app.testsupport;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainers の PostgreSQL 16 を使う試験の補助。
 *
 * <p>開発の環境と同じ初期化スクリプト ({@code compose/postgres/init.sql}) を使い、マイグレーション用の利用者 {@code app_owner} と、
 * アプリケーション用の利用者 {@code app_user} を作る。コンテナは試験の JVM の中で 1 つだけ起動し、すべての試験で共有する。
 */
public final class PostgresTestSupport {

    public static final String OWNER_USER = "app_owner";
    public static final String APP_USER = "app_user";

    private static final String OWNER_PASSWORD = "owner-test-secret";
    private static final String APP_PASSWORD = "app-test-secret";

    /** Gradle の試験は backend/ を作業ディレクトリにして動く */
    private static final Path INIT_SCRIPT = Path.of("..", "compose", "postgres", "init.sql");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("app")
            .withUsername(OWNER_USER)
            .withPassword(OWNER_PASSWORD)
            // init.sql が app_user を作るときに、psql の \getenv で読む
            .withEnv("APP_DB_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPT), "/docker-entrypoint-initdb.d/init.sql");

    static {
        POSTGRES.start();
    }

    private PostgresTestSupport() {
    }

    /**
     * アプリケーションの設定の環境変数。試験ごとに値を変えてから {@code AppConfig.fromEnv} に渡してよい。
     */
    public static Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("APP_TLS_CERT_PATH", "certs/localhost.pem");
        env.put("APP_TLS_KEY_PATH", "certs/localhost-key.pem");
        env.put("APP_DB_URL", POSTGRES.getJdbcUrl());
        env.put("APP_DB_USER", APP_USER);
        env.put("APP_DB_PASSWORD", APP_PASSWORD);
        env.put("APP_DB_MIGRATION_USER", OWNER_USER);
        env.put("APP_DB_MIGRATION_PASSWORD", OWNER_PASSWORD);
        return env;
    }

    /**
     * マイグレーション用の利用者で接続する Jdbi。試験のデータの用意に使う (アプリケーション用の利用者は書き込めない)。
     */
    public static Jdbi ownerJdbi() {
        return Jdbi.create(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD).installPlugin(new PostgresPlugin());
    }
}
