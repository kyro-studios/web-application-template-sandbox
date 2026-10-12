# フロントエンド

React + TypeScript の画面です。Vite でビルドし、ビルド結果 (`frontend/dist`) はバックエンドが配信します。

## スクリプト

| コマンド | 内容 |
| --- | --- |
| `npm ci` | 依存関係を入れる |
| `npm run dev` | 開発サーバーを `https://localhost:5173` で起動する (下の「開発サーバーの起動」を参照) |
| `npm run build` | 型を検査し、`dist/` にビルドする |
| `npm test` | Vitest と React Testing Library の試験を実行する |

## 開発サーバーの起動 (`npm run dev`)

開発の環境でも、ブラウザーから Vite、Vite からバックエンドまでのすべての区間を HTTPS にします。

1. [mkcert](https://github.com/FiloSottile/mkcert) で、ローカルの認証局を作って端末のブラウザーに信頼させ、`localhost` の証明書を作ります。リポジトリのルートで実行してください。証明書と秘密鍵はリポジトリに入れません

   ```sh
   mkcert -install
   mkdir -p certs
   mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost
   ```

2. バックエンドを `https://localhost:8443` で起動します
3. `frontend/` で、`NODE_EXTRA_CA_CERTS` に mkcert の認証局の証明書を指定して `dev` スクリプトを実行します

   ```sh
   NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" npm run dev
   ```

- Vite は `vite.config.ts` の `server.https` で `certs/localhost.pem` と `certs/localhost-key.pem` を読み、`https://localhost:5173` で待ち受けます。HTTP では待ち受けません
- `/api` への要求は `https://localhost:8443` へ中継します。中継でもバックエンドの証明書を検証する (`secure: true`) ため、Node.js が mkcert の認証局を信頼するように `NODE_EXTRA_CA_CERTS` を指定します。指定しないと、中継が証明書の検証で失敗します
- 証明書が無い場合は、開発サーバーは起動しません。`npm run build` と `npm test` は証明書が無くても動きます
