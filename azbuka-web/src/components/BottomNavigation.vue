<script setup lang="ts">
import { azbukaPath } from '../integration'
import AppIcon from './AppIcon.vue'
import { useI18n } from '../i18n'

const { t } = useI18n()
const items = [
  { to: '/', icon: 'home' as const, label: 'home' as const },
  { to: '/alphabet', icon: 'alphabet' as const, label: 'learn' as const },
  { to: '/numbers', icon: 'numbers' as const, label: 'numbers' as const },
  { to: '/practice', icon: 'practice' as const, label: 'practice' as const },
  { to: '/dictionary', icon: 'dictionary' as const, label: 'dictionary' as const },
  { to: '/profile', icon: 'profile' as const, label: 'profile' as const }
]
</script>

<template>
  <nav class="bottom-nav" aria-label="Main navigation">
    <RouterLink v-for="item in items" :key="item.to" :to="azbukaPath(item.to)" class="nav-item">
      <AppIcon :name="item.icon" />
      <span>{{ t(item.label) }}</span>
    </RouterLink>
  </nav>
</template>
<style scoped>.bottom-nav{grid-template-columns:repeat(6,minmax(0,1fr))}.bottom-nav span{font-size:10px}</style>
