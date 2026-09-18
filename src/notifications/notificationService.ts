import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'
import type { Router } from 'vue-router'
import type { NotificationPreferences } from './preferences'
import { buildRollingSchedule } from './schedule'

export interface NotificationDiagnostics {
  platform: string
  supported: boolean
  permission: 'prompt' | 'prompt-with-rationale' | 'granted' | 'denied' | 'limited'
  pending: number
  push: 'server-not-configured'
}

export async function applyNotificationSchedule(
  preferences: NotificationPreferences,
  requestPermission: boolean,
): Promise<'scheduled' | 'unsupported' | 'denied'> {
  if (!Capacitor.isNativePlatform()) return 'unsupported'

  let permission = await LocalNotifications.checkPermissions()
  const hasEnabledCategory = Object.values(preferences.categories).some((item) => item.enabled)
  if (requestPermission && hasEnabledCategory && permission.display === 'prompt') {
    permission = await LocalNotifications.requestPermissions()
  }
  if (permission.display !== 'granted') return hasEnabledCategory ? 'denied' : 'scheduled'

  const pending = await LocalNotifications.getPending()
  const owned = pending.notifications.filter((item) => item.extra?.source === 'bible-desktop-schedule')
  if (owned.length) {
    await LocalNotifications.cancel({ notifications: owned.map(({ id }) => ({ id })) })
  }

  const notifications = buildRollingSchedule(preferences)
  if (notifications.length) await LocalNotifications.schedule({ notifications })
  return 'scheduled'
}

export async function getNotificationDiagnostics(): Promise<NotificationDiagnostics> {
  const platform = Capacitor.getPlatform()
  if (!Capacitor.isNativePlatform()) {
    return { platform, supported: false, permission: 'denied', pending: 0, push: 'server-not-configured' }
  }
  const [permission, pending] = await Promise.all([
    LocalNotifications.checkPermissions(),
    LocalNotifications.getPending(),
  ])
  return {
    platform,
    supported: true,
    permission: permission.display,
    pending: pending.notifications.filter((item) => item.extra?.source === 'bible-desktop-schedule').length,
    push: 'server-not-configured',
  }
}

export async function initializeNotifications(router: Router, preferences: NotificationPreferences): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  await LocalNotifications.addListener('localNotificationActionPerformed', async ({ notification }) => {
    const route = notification.extra?.route
    if (typeof route === 'string' && route.startsWith('/')) await router.push(route)
  })
  await applyNotificationSchedule(preferences, false)
}
