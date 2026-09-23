import { createBibleApi } from './client'
import { apiBaseUrl } from '@/config/api'

export const bibleApi = createBibleApi({
  baseUrl: apiBaseUrl,
})
