import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// 開発サーバーの HTTPS 用の証明書。mkcert で作る (frontend/README.md)
const certDir = fileURLToPath(new URL('../certs/', import.meta.url))

function readCert(name: string): Buffer {
  const path = `${certDir}${name}`
  if (!existsSync(path)) {
    throw new Error(`開発用の証明書が見つかりません: ${path} (frontend/README.md の手順で作ってください)`)
  }
  return readFileSync(path)
}

export default defineConfig(({ command }) => {
  // 証明書は、開発サーバーの起動のときだけ読む。ビルドと試験では不要にする
  const isDevServer = command === 'serve' && !process.env.VITEST

  return {
    plugins: [react()],
    server: isDevServer
      ? {
          port: 5173,
          https: {
            cert: readCert('localhost.pem'),
            key: readCert('localhost-key.pem'),
          },
          proxy: {
            // バックエンド (Javalin、HTTPS) へ中継する。証明書を検証する
            '/api': {
              target: 'https://localhost:8443',
              secure: true,
            },
          },
        }
      : undefined,
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  }
})
