<script setup lang="ts">
import { computed, ref } from 'vue'
import MobileShell from '@/components/MobileShell.vue'
import {
  clearProductDiagnostics,
  diagnosticErrorIds,
  loadProductDiagnostics,
  productMetricIds,
} from '@/diagnostics/productDiagnostics'
import { useI18n } from '@/i18n'

const { messages: text } = useI18n()
const snapshot = ref(loadProductDiagnostics())
const message = ref('')
const completionRate = computed(() => {
  const opened = snapshot.value.metrics.constructor_opened?.count ?? 0
  const completed = snapshot.value.metrics.constructor_completed?.count ?? 0
  return opened ? `${Math.min(100, Math.round((completed / opened) * 100))}%` : text.value.diagnostics.noData
})

async function copyReport(): Promise<void> {
  await navigator.clipboard.writeText(JSON.stringify(snapshot.value, null, 2))
  message.value = text.value.diagnostics.copied
}

function clearReport(): void {
  if (!window.confirm(text.value.diagnostics.clearConfirm)) return
  clearProductDiagnostics()
  snapshot.value = loadProductDiagnostics()
  message.value = text.value.diagnostics.cleared
}
</script>

<template>
  <MobileShell back-to="/today">
    <section class="simple-page diagnostics-page">
      <p class="eyebrow dark-eyebrow">{{ text.diagnostics.eyebrow }}</p>
      <h1>{{ text.diagnostics.title }}</h1>
      <p>{{ text.diagnostics.intro }}</p>

      <section class="profile-panel">
        <h2>{{ text.diagnostics.journey }}</h2>
        <dl class="profile-facts">
          <div><dt>{{ text.diagnostics.completion }}</dt><dd>{{ completionRate }}</dd></div>
          <div v-for="id in productMetricIds" :key="id"><dt>{{ text.diagnostics.labels[id] }}</dt><dd>{{ snapshot.metrics[id]?.count ?? 0 }}</dd></div>
        </dl>
      </section>

      <section class="profile-panel">
        <h2>{{ text.diagnostics.errors }}</h2>
        <dl class="profile-facts">
          <div v-for="id in diagnosticErrorIds" :key="id"><dt>{{ text.diagnostics.labels[id] }}</dt><dd>{{ snapshot.errors[id]?.count ?? 0 }}</dd></div>
        </dl>
      </section>

      <div class="profile-actions">
        <button type="button" @click="copyReport">{{ text.diagnostics.copy }}</button>
        <button class="danger-text" type="button" @click="clearReport">{{ text.diagnostics.clear }}</button>
      </div>
      <p v-if="message" class="status" role="status">{{ message }}</p>
      <RouterLink class="text-action" to="/privacy">{{ text.diagnostics.dataUse }}</RouterLink>
    </section>
  </MobileShell>
</template>
