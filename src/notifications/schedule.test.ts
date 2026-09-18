import { describe, expect, it } from 'vitest'
import { defaultNotificationPreferences } from './preferences'
import { buildRollingSchedule, notificationId } from './schedule'

describe('rolling notification schedule', () => {
  it('creates fourteen future inexact reminders for one enabled category', () => {
    const preferences = defaultNotificationPreferences('08:00')
    const now = new Date(2026, 8, 18, 7, 0)

    const notifications = buildRollingSchedule(preferences, now, 14)

    expect(notifications).toHaveLength(14)
    expect(notifications[0]).toMatchObject({
      title: 'Утренняя молитва',
      isExactNotification: false,
      extra: { route: '/prayers', source: 'bible-desktop-schedule' },
    })
    expect(notifications.every((item) => item.schedule?.at && item.schedule.at > now)).toBe(true)
  })

  it('skips a reminder that already passed today', () => {
    const preferences = defaultNotificationPreferences('08:00')
    const now = new Date(2026, 8, 18, 9, 0)
    expect(buildRollingSchedule(preferences, now, 2)).toHaveLength(1)
  })

  it('generates stable platform-safe identifiers', () => {
    expect(notificationId('2026-09-18', 1)).toBe(notificationId('2026-09-18', 1))
    expect(notificationId('2026-09-18', 1)).not.toBe(notificationId('2026-09-18', 2))
    expect(notificationId('2026-09-18', 1)).toBeLessThan(2_147_483_647)
  })
})
