import { apiBaseUrl } from '@/config/api'

// Unmodified BibleDesktop assets are bundled by Vite and precached by the existing PWA.
// Resolve exact API paths only; this does not decide which sign applies to a date.
const files = import.meta.glob<string>('/src/assets/calendar/**/*.{svg,png}', { eager: true, query: '?url', import: 'default' })
export function bundledCalendarAssetUrl(source: string): string | undefined {
  try {
    const url = new URL(source, apiBaseUrl)
    if (url.origin !== new URL(apiBaseUrl).origin || url.search || url.hash) return undefined
    return files[url.pathname.replace(/^\/assets\//u, '/src/assets/calendar/')]
  } catch { return undefined }
}
