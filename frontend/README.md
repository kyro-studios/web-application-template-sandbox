# フロントエンド

React + TypeScript のアプリケーションです。Vite でビルドし、開発サーバーは `/api` をバックエンド (`https://localhost:8443`) へ中継します。

## 開発の準備

### 1. 依存関係のインストール

```bash
cd frontend
npm install
```

### 2. 開発用の HTTPS 証明書の作成

開発の環境でも、ブラウザーから Vite、Vite からバックエンドまでの区間を HTTPS にします。証明書は、[mkcert](https://github.com/FiloSottile/mkcert) で作ったローカルの認証局が発行したものを使います。証明書はリポジトリに入れず、`.gitignore` で除外します。

リポジトリのルートで、次を実行します。

```bash
mkcert -install
mkdir -p certs
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost
```

`mkcert -install` で、認証局を開発者の端末のブラウザーに信頼させます。

## スクリプト

| コマンド | 内容 |
| --- | --- |
| `npm run dev` | 開発サーバーを `https://localhost:5173` で起動する。`/api` は `https://localhost:8443` へ中継する |
| `npm run build` | 型の検査 (`tsc`) と、ビルド (`vite build`) を行い、`dist/` に出力する |
| `npm run lint` | ESLint で検査する。`react/no-danger` を有効にしており、`dangerouslySetInnerHTML` を使うとエラーになる |
| `npm test` | Vitest で試験を 1 回だけ実行する |

### `npm run dev` の使い方

`dev` スクリプトは `vite` を起動するだけです。Vite は、`vite.config.ts` で、`../certs/localhost.pem` と `../certs/localhost-key.pem` を読んで HTTPS で待ち受けます。証明書が無い場合は、起動時にその旨のエラーで止まります。

Vite の `/api` の中継は、証明書を検証します (`secure: true`)。そのため、Node.js が mkcert の認証局を信頼できるように、認証局の証明書を `NODE_EXTRA_CA_CERTS` で指定して起動します。

```bash
NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" npm run dev
```

バックエンドは、`https://localhost:8443` で起動しておく必要があります。起動していない場合、`/api` への要求は失敗します。

## 試験

試験は Vitest と React Testing Library で書きます。試験のファイルは、コンポーネントと同じ `src/` の中に `*.test.ts(x)` の名前で置きます。描画の後の片付けは `src/test/setup.ts` で行います。
