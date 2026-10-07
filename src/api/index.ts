import { createBibleApi } from './client'
import { apiBaseUrl, calendarApiBaseUrl } from '@/config/api'
import { createKalendarApi } from './kalendar'

export const bibleApi = createBibleApi({
  baseUrl: apiBaseUrl,
})

export const kalendarApi = createKalendarApi({ baseUrl: calendarApiBaseUrl })
