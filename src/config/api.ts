import defaultApiBaseUrl from '../../config/api-base-url.txt?raw'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || defaultApiBaseUrl.trim()

export const apiBaseUrl = configuredApiBaseUrl.replace(/\/+$/, '')
