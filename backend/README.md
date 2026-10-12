# バックエンド

Java 21 と Javalin で作る、HTTPS のサーバーです。`frontend/dist` の静的ファイルを配信し、API を `/api` の下に置きます。

## 必要なもの

- JDK 21
- Docker (Docker Compose)
- mkcert (開発用の証明書を作るため)

## 開発用の証明書

開発の環境でも、ブラウザーからバックエンドまでを HTTPS で通信します。リポジトリのルートで、次のコマンドを実行します。

```bash
mkcert -install
mkdir -p certs
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost
```

`certs/` は `.gitignore` で除外されているため、リポジトリには入りません。

## 環境変数

バックエンドは、次の環境変数から設定を読みます。必須の値が無い場合や、形式が正しくない場合は、起動を中止します。

| 環境変数 | 必須 | 既定値 | 内容 |
| --- | --- | --- | --- |
| `APP_HTTPS_PORT` | いいえ | `8443` | HTTPS で待ち受けるポート |
| `APP_TLS_CERT_PATH` | はい | ― | 証明書 (PEM) のパス |
| `APP_TLS_KEY_PATH` | はい | ― | 秘密鍵 (PEM) のパス |
| `APP_DB_URL` | はい | ― | JDBC の URL (例: `jdbc:postgresql://localhost:5432/app`) |
| `APP_DB_USER` | はい | ― | アプリケーション用の利用者 (`app_user`) |
| `APP_DB_PASSWORD` | はい | ― | `app_user` のパスワード |
| `APP_DB_MIGRATION_USER` | はい | ― | マイグレーション用の利用者 (`app_owner`) |
| `APP_DB_MIGRATION_PASSWORD` | はい | ― | `app_owner` のパスワード |
| `APP_DB_POOL_SIZE` | いいえ | `20` | コネクションプールの最大数 |
| `APP_DB_CONNECTION_TIMEOUT_MS` | いいえ | `1000` | コネクションを取れる時間の上限 (ミリ秒)。1000 を超える値は 1000 として扱う |
| `APP_STATIC_DIR` | いいえ | `../frontend/dist` | フロントエンドのビルド結果のディレクトリ。存在しない場合は起動を中止する |

## 起動

1. データベースを起動する (リポジトリのルートで実行する)

   ```bash
   export POSTGRES_PASSWORD='app_owner のパスワード'
   export APP_DB_PASSWORD='app_user のパスワード'
   docker compose up -d
   ```

   `compose/postgres/init.sql` が `app_user` を作ります。このスクリプトは、データのボリュームが空のときだけ実行されます。作り直す場合は `docker compose down -v` でボリュームを消してから、もう一度起動してください。`POSTGRES_PASSWORD` は `app_owner` のパスワードとして使われます。

2. 環境変数を設定して、バックエンドを起動する (`backend/` で実行する)

   ```bash
   export APP_TLS_CERT_PATH=../certs/localhost.pem
   export APP_TLS_KEY_PATH=../certs/localhost-key.pem
   export APP_DB_URL=jdbc:postgresql://localhost:5432/app
   export APP_DB_USER=app_user
   export APP_DB_PASSWORD='app_user のパスワード'
   export APP_DB_MIGRATION_USER=app_owner
   export APP_DB_MIGRATION_PASSWORD='app_owner のパスワード'

   ./gradlew run
   ```

   `https://localhost:8443/` で画面を開けます。`mkcert -install` で認証局を信頼させていれば、証明書の警告は出ません。

## テスト

`backend/` で次のコマンドを実行します。ビルドとすべての試験を行います。

```bash
./gradlew build
```

データベースの試験は Testcontainers で PostgreSQL 16 のコンテナを起動するため、Docker が必要です。
