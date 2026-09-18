import { createBibleApi } from './client'

export const bibleApi = createBibleApi({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'https://bible-desktop.com/api',
})
