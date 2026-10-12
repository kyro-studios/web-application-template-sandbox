package com.example.app.testsupport;

import java.nio.file.Path;
import java.util.Map;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainers の PostgreSQL 16 を用意する。初期化は、開発と同じ {@code compose/postgres/init.sql} を使う (認証機能の設計 7.6)。
 *
 * <p>共有のコンテナは、試験の JVM で 1 つだけ起動する。データベースを止める試験は、{@link #startContainer()} で別のコンテナを使う。
 */
public final class PostgresTestSupport {

    static final String OWNER = "app_owner";
    static final String OWNER_PASSWORD = "owner_password";
    static final String APP_USER = "app_user";
    static final String APP_PASSWORD = "app_user_password";

    private static PostgreSQLContainer<?> shared;

    private PostgresTestSupport() {
    }

    /** 試験の JVM で共有するコンテナの、アプリケーションの設定 (環境変数の形) */
    public static synchronized Map<String, String> env() {
        return env(sharedContainer());
    }

    /** 共有のコンテナの、マイグレーション用の利用者の Jdbi。データの用意と確認に使う */
    public static synchronized Jdbi ownerJdbi() {
        return ownerJdbi(sharedContainer());
    }

    /** 新しいコンテナを起動する。呼び出し側が止める */
    public static PostgreSQLContainer<?> startContainer() {
        PostgreSQLContainer<?> container = newContainer();
        container.start();
        return container;
    }

    public static Map<String, String> env(PostgreSQLContainer<?> container) {
        return Map.of(
                "APP_DB_URL", container.getJdbcUrl(),
                "APP_DB_USER", APP_USER,
                "APP_DB_PASSWORD", APP_PASSWORD,
                "APP_DB_MIGRATION_USER", OWNER,
                "APP_DB_MIGRATION_PASSWORD", OWNER_PASSWORD);
    }

    public static Jdbi ownerJdbi(PostgreSQLContainer<?> container) {
        return Jdbi.create(container.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    private static PostgreSQLContainer<?> sharedContainer() {
        if (shared == null) {
            shared = startContainer();
            Runtime.getRuntime().addShutdownHook(new Thread(shared::stop));
        }
        return shared;
    }

    private static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("app")
                .withUsername(OWNER)
                .withPassword(OWNER_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(Path.of("../compose/postgres/init.sql")),
                        "/docker-entrypoint-initdb.d/init.sql");
    }
}
