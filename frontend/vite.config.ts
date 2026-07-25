import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      // アプリシェルだけを precache する。データは常にオンラインから取る
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        // /api と /mcp は network-only (仕様書 §8: データの陳腐化防止)
        navigateFallbackDenylist: [/^\/api\//, /^\/mcp/, /^\/oauth2\//, /^\/login\//],
        runtimeCaching: [
          {
            urlPattern: /^\/api\//,
            handler: 'NetworkOnly',
          },
        ],
      },
      manifest: {
        name: 'cc-tasks — Claude Code タスクメモ',
        short_name: 'cc-tasks',
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
    port: 8931,
    // 開発時のみ Vite dev server → Spring にプロキシする。
    // 本番はオリジン 1 個なのでプロキシも CORS も不要 (仕様書 §3)
    proxy: {
      '/api': { target: 'http://localhost:7000', changeOrigin: false },
      '/mcp': { target: 'http://localhost:7000', changeOrigin: false },
      '/oauth2': { target: 'http://localhost:7000', changeOrigin: false },
      '/login': { target: 'http://localhost:7000', changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
