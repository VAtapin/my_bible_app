import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: [
        'brand/favicon-192.png',
        'brand/app-icon-512.png',
        'brand/apple-touch-icon.png',
      ],
      manifest: {
        name: 'Bible Desktop — Моё приложение',
        short_name: 'Bible Desktop',
        description: 'Персональное приложение Bible Desktop',
        lang: 'ru',
        theme_color: '#4a6b8a',
        background_color: '#f7f5f1',
        display: 'standalone',
        start_url: '/',
        icons: [
          {
            src: '/brand/favicon-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/brand/app-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      workbox: {
        navigateFallback: '/index.html',
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/'),
            handler: 'NetworkOnly',
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
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    clearMocks: true,
  },
})
