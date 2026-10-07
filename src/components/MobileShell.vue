<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { educationSettings } from '@/profile/configuration'

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
const profile = useProfileStore()
const hasReading = computed(() => profile.configuration?.sections.includes('bible'))
const hasEducation = computed(() => educationSettings(profile.configuration).pluginIds.length > 0)

function updateConnectionStatus(): void {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  if (!profile.loaded) profile.load()
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
      <RouterLink v-if="hasReading" to="/reader" :class="{ active: route.path === '/reader' }">
        <img src="/app-icons/library.png" alt="" />
        <span>{{ text.navigation.reading }}</span>
      </RouterLink>
      <RouterLink v-if="hasEducation" to="/education" :class="{ active: route.path.startsWith('/education') }">
        <img src="/app-icons/library.png" alt="" /><span>{{ text.navigation.education }}</span>
      </RouterLink>
      <RouterLink to="/more" :class="{ active: ['/more', '/profile', '/storage', '/notifications', '/diagnostics'].includes(route.path) || route.path.startsWith('/setup') }">
        <img src="/app-icons/setup.png" alt="" />
        <span>{{ text.navigation.more }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
