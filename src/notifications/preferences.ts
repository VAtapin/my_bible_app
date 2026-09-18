export const notificationCategoryIds = ['morning', 'evening', 'reading', 'calendar'] as const
export type NotificationCategoryId = typeof notificationCategoryIds[number]

export interface NotificationCategoryPreference {
  enabled: boolean
  time: string
}

export interface NotificationPreferences {
  version: 1
  categories: Record<NotificationCategoryId, NotificationCategoryPreference>
  updatedAt: string
}

const storageKey = 'bible-desktop:notifications:v1'

export function defaultNotificationPreferences(time = '08:00'): NotificationPreferences {
  return {
    version: 1,
    categories: {
      morning: { enabled: true, time },
      evening: { enabled: false, time: '21:00' },
      reading: { enabled: false, time },
      calendar: { enabled: false, time: '09:00' },
    },
    updatedAt: new Date(0).toISOString(),
  }
}

export function loadNotificationPreferences(): NotificationPreferences {
  const raw = localStorage.getItem(storageKey)
  if (!raw) return defaultNotificationPreferences()
  try {
    const value: unknown = JSON.parse(raw)
    return isNotificationPreferences(value) ? value : defaultNotificationPreferences()
  } catch {
    return defaultNotificationPreferences()
  }
}

export function saveNotificationPreferences(value: NotificationPreferences): void {
  localStorage.setItem(storageKey, JSON.stringify(value))
}

export function isNotificationPreferences(value: unknown): value is NotificationPreferences {
  if (typeof value !== 'object' || value === null || !('categories' in value)) return false
  const categories = (value as { categories?: unknown }).categories
  if (typeof categories !== 'object' || categories === null) return false
  return notificationCategoryIds.every((id) => {
    const item = (categories as Record<string, unknown>)[id]
    return typeof item === 'object' && item !== null
      && typeof (item as { enabled?: unknown }).enabled === 'boolean'
      && typeof (item as { time?: unknown }).time === 'string'
      && /^([01]\d|2[0-3]):[0-5]\d$/.test((item as { time: string }).time)
  })
}
