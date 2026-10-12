import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// 開発用の証明書 (mkcert で作る。リポジトリには入れない。アーキテクチャ設計 4 章)
const certFile = fileURLToPath(new URL('../certs/localhost.pem', import.meta.url));
const keyFile = fileURLToPath(new URL('../certs/localhost-key.pem', import.meta.url));

export default defineConfig(({ command }) => {
  // 証明書は開発サーバーでだけ読む。ビルドと試験は証明書が無くても動くようにする
  const isDevServer = command === 'serve' && !process.env.VITEST;

  return {
    plugins: [react()],
    server: {
      host: 'localhost',
      port: 5173,
      strictPort: true,
      https: isDevServer ? { cert: readFileSync(certFile), key: readFileSync(keyFile) } : undefined,
      proxy: {
        '/api': {
          target: 'https://localhost:8443',
          // 中継でも証明書を検証する。mkcert の認証局は NODE_EXTRA_CA_CERTS で信頼させる
          secure: true,
        },
      },
    },
    build: {
      outDir: 'dist',
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
