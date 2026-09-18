import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { registerSW } from 'virtual:pwa-register'
import App from './App.vue'
import { router } from './app/router'
import { initializeNotifications } from './notifications/notificationService'
import { loadNotificationPreferences } from './notifications/preferences'
import './styles.css'

registerSW({ immediate: true })

createApp(App).use(createPinia()).use(router).mount('#app')

void router.isReady().then(() => initializeNotifications(router, loadNotificationPreferences()))
