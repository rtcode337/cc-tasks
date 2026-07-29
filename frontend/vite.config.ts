import { execSync } from 'node:child_process'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

/**
 * ビルド番号(JST の日付 + コミット短縮ハッシュ)。フッターに出して、
 * いま動いているのがどのビルドか(SW の旧キャッシュ・未更新のイメージ)を見分けられるようにする。
 * Docker のビルドコンテキストには .git を含めないため、CI からは build-arg の GIT_SHA で受け取り、
 * 手元のビルドでは git から直接引く。どちらも無ければ nogit。
 */
function buildNumber(): string {
  let sha = process.env.GIT_SHA?.trim().slice(0, 7)
  if (!sha) {
    try {
      sha = execSync('git rev-parse --short=7 HEAD', { stdio: ['ignore', 'pipe', 'ignore'] })
        .toString()
        .trim()
    } catch {
      sha = 'nogit'
    }
  }
  // sv-SE ロケールは YYYY-MM-DD 固定なので、タイムゾーンだけ JST を指定して日付にする
  const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Tokyo' })
    .format(new Date())
    .replaceAll('-', '')
  return `${date}-${sha}`
}

export default defineConfig({
  define: {
    __BUILD_NUMBER__: JSON.stringify(buildNumber()),
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      // アプリシェルだけを precache する。データは常にオンラインから取る
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        // /api は network-only (仕様書 §8: データの陳腐化防止)
        navigateFallbackDenylist: [/^\/api\//, /^\/oauth2\//, /^\/login\//],
        runtimeCaching: [
          {
            urlPattern: /^\/api\//,
            handler: 'NetworkOnly',
          },
        ],
      },
      manifest: {
        name: 'CC Tasks — Claude Code タスクメモ',
        short_name: 'CC Tasks',
        description: 'Claude Code に依頼したいタスクの待ち行列',
        theme_color: '#1f2933',
        background_color: '#12181f',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        scope: '/',
        lang: 'ja',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          {
            src: '/icons/icon-512-maskable.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 7001,
    // 開発時のみ Vite dev server → Spring にプロキシする。
    // 本番はオリジン 1 個なのでプロキシも CORS も不要 (仕様書 §3)
    proxy: {
      '/api': { target: 'http://localhost:7000', changeOrigin: false },
      '/oauth2': { target: 'http://localhost:7000', changeOrigin: false },
      '/login': { target: 'http://localhost:7000', changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
