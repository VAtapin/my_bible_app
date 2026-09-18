<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import {
  defaultNotificationPreferences,
  loadNotificationPreferences,
  notificationCategoryIds,
  saveNotificationPreferences,
  type NotificationCategoryId,
  type NotificationPreferences,
} from '@/notifications/preferences'
import { applyNotificationSchedule, getNotificationDiagnostics, type NotificationDiagnostics } from '@/notifications/notificationService'
import { useProfileStore } from '@/stores/profileStore'

const categoryLabels: Record<NotificationCategoryId, { title: string; description: string }> = {
  morning: { title: 'Утренняя молитва', description: 'Спокойное начало дня' },
  evening: { title: 'Вечерняя молитва', description: 'Правило перед сном' },
  reading: { title: 'Чтение Библии', description: 'Продолжить с последнего места' },
  calendar: { title: 'Церковный календарь', description: 'Память и чтения дня' },
}

const route = useRoute()
const router = useRouter()
const profile = useProfileStore()
const preferences = ref<NotificationPreferences>(defaultNotificationPreferences())
const diagnostics = ref<NotificationDiagnostics>()
const message = ref('')
const busy = ref(false)
const isOnboarding = computed(() => route.query.onboarding === '1')

onMounted(async () => {
  profile.load()
  const stored = loadNotificationPreferences()
  if (stored.updatedAt === new Date(0).toISOString() && profile.configuration) {
    const time = profile.configuration.notifications.time
    stored.categories.morning = { enabled: profile.configuration.prayers.morning, time }
    stored.categories.evening = { enabled: profile.configuration.prayers.evening, time: '21:00' }
    stored.categories.reading = { enabled: profile.configuration.sections.includes('bible'), time }
    stored.categories.calendar = { enabled: profile.configuration.sections.includes('calendar'), time: '09:00' }
  }
  preferences.value = stored
  await refreshDiagnostics()
})

async function save(): Promise<void> {
  busy.value = true
  message.value = ''
  try {
    preferences.value.updatedAt = new Date().toISOString()
    saveNotificationPreferences(preferences.value)
    const result = await applyNotificationSchedule(preferences.value, true)
    message.value = result === 'scheduled'
      ? 'Расписание обновлено на ближайшие 14 дней.'
      : result === 'unsupported'
        ? 'Настройки сохранены. Системные уведомления работают в Android и iOS.'
        : 'Настройки сохранены, но системное разрешение не предоставлено.'
    await refreshDiagnostics()
    if (isOnboarding.value) window.setTimeout(() => { void router.push('/today') }, 700)
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Не удалось обновить уведомления.'
  } finally {
    busy.value = false
  }
}

async function refreshDiagnostics(): Promise<void> {
  diagnostics.value = await getNotificationDiagnostics()
}
</script>

<template>
  <MobileShell :back-to="isOnboarding ? '/today' : '/setup/manual?edit=1'">
    <section class="simple-page notification-page">
      <p class="eyebrow dark-eyebrow">Напоминания</p>
      <h1>Ваш ритм</h1>
      <p>Включите только нужные категории. Системное разрешение будет запрошено после сохранения.</p>

      <section class="notification-list">
        <div v-for="category in notificationCategoryIds" :key="category" class="notification-row">
          <label class="toggle-row">
            <span><strong>{{ categoryLabels[category].title }}</strong><small>{{ categoryLabels[category].description }}</small></span>
            <input v-model="preferences.categories[category].enabled" type="checkbox" />
          </label>
          <label v-if="preferences.categories[category].enabled" class="time-row">
            <span>Время</span>
            <input v-model="preferences.categories[category].time" type="time" />
          </label>
        </div>
      </section>

      <button class="primary-action" type="button" :disabled="busy" @click="save">
        {{ busy ? 'Сохраняем…' : 'Сохранить расписание' }}
      </button>
      <p v-if="message" class="status" role="status">{{ message }}</p>

      <section v-if="diagnostics" class="diagnostics-card">
        <div><span>Платформа</span><strong>{{ diagnostics.platform }}</strong></div>
        <div><span>Системное разрешение</span><strong>{{ diagnostics.supported ? diagnostics.permission : 'только Android/iOS' }}</strong></div>
        <div><span>Запланировано</span><strong>{{ diagnostics.pending }}</strong></div>
        <div><span>Push-обновления</span><strong>ожидают серверный endpoint</strong></div>
      </section>
    </section>
  </MobileShell>
</template>
