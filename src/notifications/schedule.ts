import type { LocalNotificationSchema } from '@capacitor/local-notifications'
import { addCalendarDays, calendarDateInTimeZone } from '@/services/calendarDates'
import type { NotificationCategoryId, NotificationPreferences } from './preferences'

const categoryInfo: Record<NotificationCategoryId, { id: number; title: string; body: string; route: string }> = {
  morning: { id: 1, title: 'Утренняя молитва', body: 'Спокойное время для молитвенного правила.', route: '/prayers' },
  evening: { id: 2, title: 'Вечерняя молитва', body: 'Завершите день молитвой.', route: '/prayers' },
  reading: { id: 3, title: 'Чтение Библии', body: 'Продолжите с последнего места.', route: '/reader' },
  calendar: { id: 4, title: 'Церковный календарь', body: 'Откройте память и чтения дня.', route: '/calendar' },
}

export function buildRollingSchedule(
  preferences: NotificationPreferences,
  now = new Date(),
  days = 14,
): LocalNotificationSchema[] {
  const today = calendarDateInTimeZone(now, Intl.DateTimeFormat().resolvedOptions().timeZone)
  const notifications: LocalNotificationSchema[] = []

  for (let dayOffset = 0; dayOffset < days; dayOffset += 1) {
    const date = addCalendarDays(today, dayOffset)
    for (const [category, preference] of Object.entries(preferences.categories) as Array<
      [NotificationCategoryId, NotificationPreferences['categories'][NotificationCategoryId]]
    >) {
      if (!preference.enabled) continue
      const at = localDateTime(date, preference.time)
      if (at <= now) continue
      const info = categoryInfo[category]
      notifications.push({
        id: notificationId(date, info.id),
        title: info.title,
        body: info.body,
        schedule: { at, allowWhileIdle: false },
        isExactNotification: false,
        autoCancel: true,
        extra: { source: 'bible-desktop-schedule', category, route: info.route, date },
      })
    }
  }

  return notifications
}

export function notificationId(date: string, categoryId: number): number {
  const epochDay = Math.floor(Date.parse(`${date}T12:00:00Z`) / 86_400_000)
  return (epochDay % 100_000) * 10 + categoryId
}

function localDateTime(date: string, time: string): Date {
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  return new Date(year, month - 1, day, hour, minute, 0, 0)
}
