<script setup lang="ts">
import { RouterLink } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
const { messages: text } = useI18n()
const items = [
  { route: '/setup/manual?edit=1', title: 'customize', description: 'setupDescription', icon: 'setup' },
  { route: '/notifications', title: 'notificationsTitle', description: 'notificationsDescription', icon: 'calendar' },
  { route: '/profile', title: 'profileTitle', description: 'profileDescription', icon: 'bookmarks' },
  { route: '/storage', title: 'storageTitle', description: 'storageDescription', icon: 'library' },
  { route: '/diagnostics', title: 'diagnosticsTitle', description: 'diagnosticsDescription', icon: 'setup' },
] as const
</script>
<template>
  <MobileShell back-to="/today">
    <h1 class="compact-page-title">{{ text.navigation.more }}</h1>
    <div class="module-list">
      <RouterLink v-for="item in items" :key="item.route" :to="item.route" class="module-card available">
        <span class="module-icon"><img :src="`/app-icons/${item.icon}.png`" alt="" /></span>
        <span><strong>{{ text.today[item.title] }}</strong><small>{{ text.today[item.description] }}</small></span><span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </MobileShell>
</template>
