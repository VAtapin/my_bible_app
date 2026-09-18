<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from '@/i18n'

withDefaults(defineProps<{
  showNavigation?: boolean
  backTo?: string
}>(), {
  showNavigation: true,
  backTo: undefined,
})

const route = useRoute()
const { messages: text } = useI18n()
const isOnline = ref(navigator.onLine)

function updateConnectionStatus(): void {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  window.addEventListener('online', updateConnectionStatus)
  window.addEventListener('offline', updateConnectionStatus)
})

onUnmounted(() => {
  window.removeEventListener('online', updateConnectionStatus)
  window.removeEventListener('offline', updateConnectionStatus)
})
</script>

<template>
  <div class="mobile-app">
    <header class="app-header">
      <RouterLink v-if="backTo" :to="backTo" class="back-link" :aria-label="text.navigation.back">
        <span aria-hidden="true">←</span>
      </RouterLink>
      <div class="brand-lockup">
        <img src="/brand/bible-desktop-mark.png" alt="" />
        <span>
          <strong>{{ text.brand }}</strong>
          <small>{{ text.brandSubtitle }}</small>
        </span>
      </div>
      <div class="connection" :class="{ offline: !isOnline }">
        <span aria-hidden="true"></span>
        {{ isOnline ? text.online : text.offline }}
      </div>
    </header>

    <main class="app-content">
      <slot />
    </main>

    <nav v-if="showNavigation" class="bottom-nav" :aria-label="text.navigation.label">
      <RouterLink to="/today" :class="{ active: route.path === '/today' }">
        <img src="/app-icons/calendar.png" alt="" />
        <span>{{ text.navigation.today }}</span>
      </RouterLink>
      <RouterLink to="/reader" :class="{ active: route.path === '/reader' }">
        <img src="/app-icons/library.png" alt="" />
        <span>{{ text.navigation.reading }}</span>
      </RouterLink>
      <RouterLink to="/setup/manual?edit=1" :class="{ active: route.path.startsWith('/setup') }">
        <img src="/app-icons/setup.png" alt="" />
        <span>{{ text.navigation.settings }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
