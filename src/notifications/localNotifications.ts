import { Capacitor } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'

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
        title: 'My Bible App',
        body: 'Тестовое напоминание работает.',
        schedule: { at: new Date(Date.now() + 10_000) },
      },
    ],
  })

  return 'scheduled'
}
