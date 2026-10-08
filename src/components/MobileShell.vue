<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { useAppearance } from '@/profile/appearance'
import AppIcon from '../../azbuka-web/src/components/AppIcon.vue'

withDefaults(defineProps<{
  showNavigation?: boolean
  showHeader?: boolean
  backTo?: string
}>(), {
  showNavigation: true,
  showHeader: true,
  backTo: undefined,
})

const route = useRoute()
const { messages: text } = useI18n()
const isOnline = ref(navigator.onLine)
const profile = useProfileStore()
const appearance = useAppearance()

function updateConnectionStatus(): void {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  appearance.initialize()
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
  <div class="mobile-app" :class="{ 'without-header': !showHeader, 'without-navigation': !showNavigation }" :data-theme="appearance.theme.value">
    <header v-if="showHeader" class="app-header">
      <RouterLink v-if="backTo" :to="backTo" class="back-link" :aria-label="text.navigation.back">
        <span aria-hidden="true">←</span>
      </RouterLink>
      <RouterLink class="brand-lockup" :to="profile.configuration ? '/today' : '/'" :aria-label="text.navigation.today">
        <img src="/brand/bible-desktop-mark.png" alt="" />
        <span>
          <strong>{{ text.brand }}</strong>
          <small>{{ text.brandSubtitle }}</small>
        </span>
      </RouterLink>
      <div class="connection" :class="{ offline: !isOnline }">
        <span aria-hidden="true"></span>
        {{ isOnline ? text.online : text.offline }}
      </div>
    </header>

    <main class="app-content">
      <slot />
    </main>

    <slot v-if="showNavigation" name="footer">
    <nav class="bottom-nav" :aria-label="text.navigation.label">
      <RouterLink to="/today" :class="{ active: route.path === '/today' }">
        <AppIcon name="home" />
        <span>{{ text.navigation.today }}</span>
      </RouterLink>
      <RouterLink to="/reader" :class="{ active: route.path === '/reader' }">
        <img src="/app-icons/library.png" alt="" />
        <span>{{ text.sections.bible.title }}</span>
      </RouterLink>
      <RouterLink to="/prayers" :class="{ active: route.path.startsWith('/prayers') || route.path.startsWith('/liturgical') }">
        <img src="/app-icons/prayers.png" alt="" /><span>{{ text.navigation.prayers }}</span>
      </RouterLink>
      <RouterLink to="/calendar" :class="{ active: route.path === '/calendar' }">
        <img src="/app-icons/calendar.png" alt="" /><span>{{ text.navigation.calendar }}</span>
      </RouterLink>
      <RouterLink to="/more" :class="{ active: ['/more', '/profile', '/storage', '/notifications', '/diagnostics'].includes(route.path) || route.path.startsWith('/setup') }">
        <AppIcon name="more" />
        <span>{{ text.navigation.more }}</span>
      </RouterLink>
    </nav>
    </slot>
  </div>
</template>
