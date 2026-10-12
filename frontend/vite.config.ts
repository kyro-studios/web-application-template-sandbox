/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 開発用の証明書 (mkcert で作る。リポジトリには入れない。アーキテクチャ設計 4 章)
const certPath = new URL('../certs/localhost.pem', import.meta.url);
const keyPath = new URL('../certs/localhost-key.pem', import.meta.url);

export default defineConfig(({ command, mode }) => {
  // 証明書は開発サーバー (vite、vite preview) を起動するときだけ読む。
  // ビルドと試験は、証明書が無い環境 (CI など) でも動くようにする
  const usesServer = command === 'serve' && mode !== 'test';
  const https = usesServer
    ? { cert: readFileSync(certPath), key: readFileSync(keyPath) }
    : undefined;

  return {
    plugins: [react()],
    build: {
      outDir: 'dist',
    },
    server: {
      https,
      host: 'localhost',
      port: 5173,
      strictPort: true,
      proxy: {
        // 中継でも証明書を検証する。NODE_EXTRA_CA_CERTS に mkcert の認証局を指定して起動する
        '/api': {
          target: 'https://localhost:8443',
          secure: true,
        },
      },
    },
    preview: {
      https,
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
