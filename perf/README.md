# 性能試験 (perf)

## お知らせの一覧 (NFR-3)

1,000 件が登録された状態で、一覧の取得 (`GET /api/announcements`) の 95% が 1 秒以内に完了することを確かめます (仕様書 10.3、アーキテクチャ設計 7.2)。

試験の条件は、100 人 (仮想ユーザー) が 1 分間、一覧を繰り返し取得するものです。この条件は設計の確認のためのもので、要件ではありません。

しきい値 (`perf/announcements.js` の `thresholds`) は次のとおりです。

| 項目 | しきい値 |
| --- | --- |
| `http_req_duration` | 95 パーセンタイルが 1,000 ミリ秒以内 |
| `http_req_failed` | 失敗が 0 件 |
| `checks` | 応答が 200 で、`announcements` の長さが 20 |

以下は、リポジトリのルートで実行します。

### 1. 開発の環境の PostgreSQL を起動する

```bash
docker compose up -d postgres
```

### 2. バックエンドを起動する

起動のときに Flyway がテーブルを作ります。データを入れる前に、一度起動してください。Java 21 が必要です。

```bash
cd backend
APP_TLS_CERT_PATH=src/test/resources/tls/test-cert.pem \
APP_TLS_KEY_PATH=src/test/resources/tls/test-key.pem \
APP_DB_URL=jdbc:postgresql://localhost:5432/app \
APP_DB_USER=app_user \
APP_DB_PASSWORD=app_user_password \
APP_DB_MIGRATION_USER=app_owner \
APP_DB_MIGRATION_PASSWORD=owner_password \
./gradlew run
```

`https://localhost:8443` で待ち受けます。停止するまで、このターミナルは開いたままにします。

### 3. 1,000 件を入れる

別のターミナルで、リポジトリのルートから実行します。

```bash
docker compose exec -T postgres psql -U app_owner -d app < perf/seed-announcements.sql
```

`announcements` を空にしてから、公開中 500 件と公開前 500 件を入れます。開発の環境のデータは消えます。何度実行しても、1,000 件になります。

入ったことは、次の SQL で確かめます。公開中が 500、公開前が 500 になります。

```bash
docker compose exec -T postgres psql -U app_owner -d app -c \
  "SELECT count(*) FILTER (WHERE published_at <= now()) AS published, count(*) FILTER (WHERE published_at > now()) AS unpublished FROM announcements;"
```

### 4. k6 で試験を実行する

k6 を入れている場合は、次のコマンドで実行します。

```bash
k6 run perf/announcements.js
```

入れていない場合は、Docker の `grafana/k6` イメージを使います。Linux では、ホストのネットワークを使うと `localhost` にそのまま届きます。

```bash
docker run --rm -i --network host -v "$PWD/perf:/perf:ro" grafana/k6 run /perf/announcements.js
```

Docker Desktop (macOS、Windows) では `--network host` が使えないため、`-e BASE_URL=https://host.docker.internal:8443` を付けて実行します。

しきい値を 1 つでも超えると、k6 は終了コード 99 で終わり、結果の `thresholds` に `✗` が付きます。

### 5. 後始末

バックエンドは `Ctrl+C` で止めます。PostgreSQL は、次のコマンドで止めます。

```bash
docker compose down
```
