<script setup lang="ts">
import { computed, ref } from 'vue'
import MobileShell from '@/components/MobileShell.vue'
import {
  clearProductDiagnostics,
  diagnosticErrorIds,
  loadProductDiagnostics,
  productMetricIds,
} from '@/diagnostics/productDiagnostics'

const labels: Record<string, string> = {
  app_opened: 'Открытия приложения',
  constructor_opened: 'Открытия конструктора',
  constructor_completed: 'Завершения конструктора',
  profile_created: 'Создания серверного профиля',
  offline_download_completed: 'Успешные офлайн-загрузки',
  notification_schedule_saved: 'Сохранения расписания',
  unexpected: 'Неожиданные ошибки',
  offline_download: 'Ошибки офлайн-загрузки',
  profile_restore: 'Ошибки восстановления',
  profile_sync: 'Ошибки синхронизации',
  notification_schedule: 'Ошибки расписания',
}

const snapshot = ref(loadProductDiagnostics())
const message = ref('')
const completionRate = computed(() => {
  const opened = snapshot.value.metrics.constructor_opened?.count ?? 0
  const completed = snapshot.value.metrics.constructor_completed?.count ?? 0
  return opened ? `${Math.min(100, Math.round((completed / opened) * 100))}%` : 'нет данных'
})

async function copyReport(): Promise<void> {
  await navigator.clipboard.writeText(JSON.stringify(snapshot.value, null, 2))
  message.value = 'Обезличенная сводка скопирована.'
}

function clearReport(): void {
  if (!window.confirm('Очистить локальную диагностическую сводку?')) return
  clearProductDiagnostics()
  snapshot.value = loadProductDiagnostics()
  message.value = 'Локальная сводка очищена.'
}
</script>

<template>
  <MobileShell back-to="/today">
    <section class="simple-page diagnostics-page">
      <p class="eyebrow dark-eyebrow">Поддержка пилота</p>
      <h1>Диагностика</h1>
      <p>Сводка хранится только на этом устройстве и не содержит текстов, запросов, ссылок или токенов.</p>

      <section class="profile-panel">
        <h2>Ключевой путь</h2>
        <dl class="profile-facts">
          <div><dt>Завершение конструктора</dt><dd>{{ completionRate }}</dd></div>
          <div v-for="id in productMetricIds" :key="id"><dt>{{ labels[id] }}</dt><dd>{{ snapshot.metrics[id]?.count ?? 0 }}</dd></div>
        </dl>
      </section>

      <section class="profile-panel">
        <h2>Ошибки по категориям</h2>
        <dl class="profile-facts">
          <div v-for="id in diagnosticErrorIds" :key="id"><dt>{{ labels[id] }}</dt><dd>{{ snapshot.errors[id]?.count ?? 0 }}</dd></div>
        </dl>
      </section>

      <div class="profile-actions">
        <button type="button" @click="copyReport">Скопировать сводку</button>
        <button class="danger-text" type="button" @click="clearReport">Очистить</button>
      </div>
      <p v-if="message" class="status" role="status">{{ message }}</p>
      <RouterLink class="text-action" to="/privacy">Как используются данные</RouterLink>
    </section>
  </MobileShell>
</template>
