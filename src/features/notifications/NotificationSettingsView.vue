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
import { recordProductMetric, recordSanitizedError } from '@/diagnostics/productDiagnostics'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const profile = useProfileStore()
const { messages: text } = useI18n()
const categoryLabels = computed<Record<NotificationCategoryId, { title: string; description: string }>>(() => ({
  morning: { title: text.value.notifications.morning, description: text.value.notifications.morningHint },
  evening: { title: text.value.notifications.evening, description: text.value.notifications.eveningHint },
  reading: { title: text.value.notifications.reading, description: text.value.notifications.readingHint },
  calendar: { title: text.value.notifications.calendar, description: text.value.notifications.calendarHint },
}))
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
    recordProductMetric('notification_schedule_saved')
    message.value = result === 'scheduled'
      ? text.value.notifications.scheduleUpdated
      : result === 'unsupported'
        ? text.value.notifications.saved
        : text.value.notifications.denied
    await refreshDiagnostics()
    if (isOnboarding.value) window.setTimeout(() => { void router.push('/today') }, 700)
  } catch (error) {
    recordSanitizedError('notification_schedule')
    message.value = error instanceof Error ? error.message : text.value.notifications.failed
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
      <p class="eyebrow dark-eyebrow">{{ text.notifications.eyebrow }}</p>
      <h1>{{ text.notifications.title }}</h1>
      <p>{{ text.notifications.intro }}</p>

      <section class="notification-list">
        <div v-for="category in notificationCategoryIds" :key="category" class="notification-row">
          <label class="toggle-row">
            <span><strong>{{ categoryLabels[category].title }}</strong><small>{{ categoryLabels[category].description }}</small></span>
            <input v-model="preferences.categories[category].enabled" type="checkbox" />
          </label>
          <label v-if="preferences.categories[category].enabled" class="time-row">
            <span>{{ text.notifications.time }}</span>
            <input v-model="preferences.categories[category].time" type="time" />
          </label>
        </div>
      </section>

      <button class="primary-action" type="button" :disabled="busy" @click="save">
        {{ busy ? text.notifications.saving : text.notifications.save }}
      </button>
      <p v-if="message" class="status" role="status">{{ message }}</p>

      <section v-if="diagnostics" class="diagnostics-card">
        <div><span>{{ text.notifications.platform }}</span><strong>{{ diagnostics.platform }}</strong></div>
        <div><span>{{ text.notifications.permission }}</span><strong>{{ diagnostics.supported ? diagnostics.permission : text.notifications.nativeOnly }}</strong></div>
        <div><span>{{ text.notifications.pending }}</span><strong>{{ diagnostics.pending }}</strong></div>
        <div><span>{{ text.notifications.push }}</span><strong>{{ text.notifications.pushStatus }}</strong></div>
      </section>
    </section>
  </MobileShell>
</template>
