import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { registerSW } from 'virtual:pwa-register'
import App from './App.vue'
import { router } from './app/router'
import { initializeNotifications } from './notifications/notificationService'
import { loadNotificationPreferences } from './notifications/preferences'
import { flushProfileSyncQueue } from './profile/profileSync'
import { installGlobalErrorDiagnostics, recordProductMetric, recordSanitizedError } from './diagnostics/productDiagnostics'
import './styles.css'

registerSW({ immediate: true })
installGlobalErrorDiagnostics()
recordProductMetric('app_opened')

createApp(App).use(createPinia()).use(router).mount('#app')

void router.isReady().then(() => initializeNotifications(router, loadNotificationPreferences()))
window.addEventListener('online', () => {
  void flushProfileSyncQueue().catch(() => recordSanitizedError('profile_sync'))
})
void flushProfileSyncQueue().catch(() => recordSanitizedError('profile_sync'))
