import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    // Keep public root URLs in component tests (Windows cannot import /brand as a file URL).
    vue({ template: { transformAssetUrls: { includeAbsolute: !process.env.VITEST } } }),
    {
      name: 'shared-azbuka-font-license',
      generateBundle() {
        this.emitFile({ type: 'asset', fileName: 'fonts/OFL-Ponomar.txt', source: readFileSync(new URL('./azbuka-web/public/fonts/OFL-Ponomar.txt', import.meta.url), 'utf8') })
      },
    },
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: [
        'brand/favicon-192.png',
        'brand/app-icon-512.png',
        'brand/apple-touch-icon.png',
        'app-icons/bookmarks.png',
        'app-icons/calendar.png',
        'app-icons/library.png',
        'app-icons/prayers.png',
        'app-icons/setup.png',
        'data/bible-geo.json',
      ],
      manifest: {
        name: 'Bible App',
        short_name: 'Bible App',
        description: 'Personal Bible App application',
        lang: 'mul',
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
        globPatterns: ['**/*.{js,css,html,png,svg,ttf,txt}'],
        // Cache this supplied original explicitly without raising the size limit for other assets.
        globIgnores: ['brand/welcome-church.png'],
        additionalManifestEntries: [{
          url: 'brand/welcome-church.png',
          revision: createHash('sha256').update(readFileSync(new URL('./public/brand/welcome-church.png', import.meta.url))).digest('hex'),
        }],
        navigateFallback: '/index.html',
        // Native Android policy is a standalone HTML document, not a Vue route.
        navigateFallbackDenylist: [/^\/android\/privacy(?:\/|$)/],
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
    dedupe: ['vue', 'pinia', 'vue-router'],
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'azbuka-web/src/**/*.test.ts'],
    clearMocks: true,
  },
})
