<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '../../../azbuka-web/src/stores/profile'
import { useI18n as useAzbukaI18n } from '../../../azbuka-web/src/i18n'
import '../../../azbuka-web/src/styles.css'

const store = useProfileStore()
const { language, messages: text } = useI18n()
const { locale, t } = useAzbukaI18n()
const failed = ref(false)
const tabs = [
  { path: '', label: 'home' }, { path: '/alphabet', label: 'learn' },
  { path: '/numbers', label: 'numbers' }, { path: '/practice', label: 'practice' },
  { path: '/profile', label: 'profile' },
] as const
async function initialize(): Promise<void> {
  failed.value = false
  try {
    await store.initialize()
    if (!store.profile) await store.create(language.value, 10)
  } catch { failed.value = true }
}
onMounted(initialize)
</script>

<template>
  <MobileShell back-to="/education">
    <div class="azbuka-app embedded-azbuka" :data-locale="locale">
      <nav class="azbuka-tabs" :aria-label="text.education.apps.azbuka.title">
        <RouterLink v-for="tab in tabs" :key="tab.path" :to="`/education/azbuka${tab.path}`" :exact-active-class="'selected'">{{ t(tab.label) }}</RouterLink>
      </nav>
      <p v-if="failed" role="alert">{{ text.education.loadFailed }} <button type="button" @click="initialize">{{ text.education.retry }}</button></p>
      <RouterView v-else-if="store.initialized && store.profile" />
      <p v-else role="status">{{ text.loading }}</p>
    </div>
  </MobileShell>
</template>

<style>
.embedded-azbuka .page { width: 100%; min-height: 0; padding: 16px 0 24px; margin: 0; border-radius: 0; box-shadow: none; }
.embedded-azbuka .home-page .topbar { display: none; }
.embedded-azbuka .azbuka-tabs { display: flex; gap: 5px; overflow-x: auto; padding: 0 0 12px; }
.embedded-azbuka .azbuka-tabs a { padding: 10px 8px; white-space: nowrap; border-radius: 10px; font-size: 12px; color: #4a6b8a; }
.embedded-azbuka .azbuka-tabs .selected { background: #eaf1f5; font-weight: 700; }
.embedded-azbuka .answer-feedback { position: sticky; bottom: 0; width: 100%; margin: 12px 0 0; transform: none; left: auto; }
</style>
