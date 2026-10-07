import defaultApiBaseUrl from '../../config/api-base-url.txt?raw'
import defaultCalendarApiBaseUrl from '../../config/calendar-api-base-url.txt?raw'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || defaultApiBaseUrl.trim()

export const apiBaseUrl = configuredApiBaseUrl.replace(/\/+$/, '')
export const calendarApiBaseUrl = (import.meta.env.VITE_CALENDAR_API_BASE_URL?.trim() || defaultCalendarApiBaseUrl.trim()).replace(/\/+$/, '')
