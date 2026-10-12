# フロントエンド

React + TypeScript で作った画面 (SPA) です。Vite でビルドし、ビルド結果 (`frontend/dist`) をバックエンドが配信します。

## スクリプト

`frontend/` で実行します。

| コマンド | 内容 |
| --- | --- |
| `npm ci` | 依存関係を入れる |
| `npm run dev` | 開発サーバーを `https://localhost:5173` で起動する (下の「開発サーバーの起動」を参照) |
| `npm run build` | 型を検査し、`frontend/dist` にビルドする |
| `npm test` | Vitest と React Testing Library の試験を実行する |

## 開発サーバーの起動 (`npm run dev`)

開発の環境でも、ブラウザーから Vite、Vite からバックエンドまでのすべての区間を HTTPS にします。

### 1. 証明書を作る (初回だけ)

[mkcert](https://github.com/FiloSottile/mkcert) で、ローカルの認証局と `localhost` の証明書を作ります。リポジトリのルートで実行します。証明書と秘密鍵はリポジトリに入れません (`certs/` は `.gitignore` で除外しています)。

```sh
mkcert -install
mkdir -p certs
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost
```

### 2. 起動する

Vite は `/api` への要求を `https://localhost:8443` (バックエンド) へ中継し、中継でもバックエンドの証明書を検証します。Node.js が mkcert の認証局を信頼するように、`NODE_EXTRA_CA_CERTS` に認証局の証明書を指定して起動します。

```sh
cd frontend
NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" npm run dev
```

Windows の PowerShell では、次のように起動します。

```powershell
cd frontend
$env:NODE_EXTRA_CA_CERTS = "$(mkcert -CAROOT)\rootCA.pem"
npm run dev
```

- `vite.config.ts` の `server.https` に `certs/localhost.pem` と `certs/localhost-key.pem` を指定しています。開発サーバーは HTTP を受け付けません
- 証明書が無いと開発サーバーは起動しません。`npm run build` と `npm test` は証明書が無くても動きます
- バックエンドは、同じ証明書で `https://localhost:8443` に起動しておきます

## 構成

```text
src
├── main.tsx          起動。AuthProvider と Router を組み立てる
├── App.tsx           ルートの定義
├── api/              API の呼び出し (client.ts、types.ts、auth.ts)
├── auth/             ログイン状態 (AuthContext.tsx) とガード (RequireAuth.tsx、RequireGuest.tsx)
├── pages/            各画面
└── components/       FieldError.tsx、Message.tsx、LogoutButton.tsx
```

決まり (アーキテクチャ設計 3.3):

- 入力の検査はフロントエンドでは行わず、API の応答の `fieldErrors` と `message` をそのまま表示する
- どの API でも E-06 を受け取ったら、未ログインにして `/login` へ移り、E-06 のメッセージを表示する (`api/client.ts` と `auth/AuthContext.tsx`)
- ログイン中の状態を表す情報 (Cookie) は JavaScript から読まない。localStorage や sessionStorage に、ログインに関わる情報を保存しない
