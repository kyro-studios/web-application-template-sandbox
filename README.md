# Webアプリケーション開発テンプレート

このレポジトリは Coding Agent が自動的に Web Application を開発するためのテンプレートレポジトリです。
以下のフローに従って、異なるエージェント同士が協調しながら開発を進めていきます。

## 開発フロー

### 新規機能開発 (機能実装完了まで)

```typescript
let 要件定義書 = Human.要件定義(); // 機能要件 + 非機能要件

let [結果, 指摘内容] = Codex.レビュー(要件定義書);
while (!結果) {
  要件定義書 = Human.要件修正(要件定義書, 指摘内容);

  [結果, 指摘内容] = Codex.レビュー(要件定義書);
}

let 仕様書 = Claude.仕様書作成(要件定義書);

let [結果, 指摘内容] = Codex.レビュー(仕様書, 要件定義書);
while (!結果) {
  仕様書 = Claude.仕様書修正(仕様書, 要件定義書, 指摘内容);
  
  [結果, 指摘内容] = Codex.レビュー(仕様書, 要件定義書);
}

let 概念モデル = Claude.概念モデリング(仕様書);

let [結果, 指摘内容] = Codex.レビュー(概念モデル, 仕様書);
while (!結果) {
  概念モデル = Claude.概念モデル修正(概念モデル, 仕様書, 指摘内容);
  
  [結果, 指摘内容] = Codex.レビュー(概念モデル, 仕様書);
}

let アーキテクチャ設計 = Claude.アーキテクチャ設計(要件定義書, 仕様書, 概念モデル);

let [結果, 指摘内容] = Codex.レビュー(アーキテクチャ設計, 要件定義書, 仕様書, 概念モデル);
while (!結果) {
  アーキテクチャ設計 = Claude.アーキテクチャ設計修正(アーキテクチャ設計, 要件定義書, 仕様書, 概念モデル, 指摘内容);
  
  [結果, 指摘内容] = Codex.レビュー(アーキテクチャ設計, 要件定義書, 仕様書, 概念モデル);
}

const 全ての実装計画 = Claude.実装計画(仕様書, 概念モデル, アーキテクチャ設計);

while (true) {
  let 実装完了 = true;

  for (const 実装計画 of 全ての実装計画) {
    if (実装計画.ステータス() === 完了) {
      continue;
    }

    if (実装計画.ステータス() === 進行不可能) {
      実装完了 = false;
      continue;
    }

    if (実装計画.ステータス() === 進行可能) {
      Claude.実施(実装計画, 仕様書, 概念モデル, アーキテクチャ設計); // 実装計画.ステータス() → 完了
    }
  }

  if (実装完了) {
    break;
  }
}
```
## バックエンド

バックエンドは `backend/` にある Javalin のアプリケーションです (Java 21、Gradle)。ビルドには Java 21 を使います。端末に Java 21 が無い場合は、Gradle がダウンロードします。

### ビルドと試験

```sh
cd backend
./gradlew build   # コンパイルと試験
./gradlew test    # 試験だけ
```

試験は、試験の中で一時的な自己署名の証明書を作り、HTTPS のサーバーを起動して行います。証明書を用意する必要はありません。

### 開発用の証明書

バックエンドは HTTPS だけで待ち受けます。開発の環境では、[mkcert](https://github.com/FiloSottile/mkcert) で作った `localhost` の証明書を使います。証明書と秘密鍵はリポジトリに入れません (`certs/` は `.gitignore` で除外しています)。

```sh
mkcert -install   # ローカルの認証局を作り、端末のブラウザーに信頼させる (最初に 1 回だけ)
mkdir -p certs
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost
```

### 起動

```sh
cd backend
export APP_TLS_CERT_PATH=../certs/localhost.pem
export APP_TLS_KEY_PATH=../certs/localhost-key.pem
export APP_DB_URL=jdbc:postgresql://localhost:5432/app
export APP_DB_USER=app_user
export APP_DB_PASSWORD=...
export APP_DB_MIGRATION_USER=app_owner
export APP_DB_MIGRATION_PASSWORD=...
./gradlew run
```

`https://localhost:8443` で待ち受けます。設定は次の環境変数で行います。必須の値が無い場合は、起動を中止します。

| 環境変数 | 必須 | 既定値 | 意味 |
| --- | --- | --- | --- |
| `APP_HTTPS_PORT` | いいえ | `8443` | HTTPS で待ち受けるポート |
| `APP_TLS_CERT_PATH` | はい | ― | サーバー証明書 (PEM) のパス |
| `APP_TLS_KEY_PATH` | はい | ― | 秘密鍵 (PEM) のパス |
| `APP_DB_URL` | はい | ― | JDBC の URL |
| `APP_DB_USER` | はい | ― | 要求の処理と定期処理で使う、データベースの利用者名 |
| `APP_DB_PASSWORD` | はい | ― | `APP_DB_USER` のパスワード |
| `APP_DB_MIGRATION_USER` | はい | ― | マイグレーションで使う、データベースの利用者名 |
| `APP_DB_MIGRATION_PASSWORD` | はい | ― | `APP_DB_MIGRATION_USER` のパスワード |
| `APP_DB_POOL_SIZE` | いいえ | `20` | コネクションプールの最大の数 |
| `APP_STATIC_DIR` | いいえ | `../frontend/dist` | フロントエンドのビルド結果のディレクトリ |

相対パスは、`./gradlew run` では `backend/` からのパスになります。
