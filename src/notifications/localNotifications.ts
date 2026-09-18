import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'
import { ru } from '@/i18n/ru'

export async function schedulePrototypeNotification(): Promise<'scheduled' | 'unsupported' | 'denied'> {
  if (!Capacitor.isNativePlatform()) {
    return 'unsupported'
  }

  const current = await LocalNotifications.checkPermissions()
  const permission = current.display === 'prompt'
    ? await LocalNotifications.requestPermissions()
    : current

  if (permission.display !== 'granted') {
    return 'denied'
  }

  await LocalNotifications.schedule({
    notifications: [
      {
        id: 1,
        title: ru.notificationTitle,
        body: ru.notificationBody,
        schedule: { at: new Date(Date.now() + 10_000) },
      },
    ],
  })

  return 'scheduled'
}
