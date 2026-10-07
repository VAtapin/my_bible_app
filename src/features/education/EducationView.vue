<script setup lang="ts">
import { RouterLink } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { learningApps } from './learningApps'

const { messages: text } = useI18n()
</script>

<template>
  <MobileShell :back-to="'/today'">
    <section class="today-hero">
      <p class="eyebrow">{{ text.education.eyebrow }}</p>
      <h1>{{ text.education.title }}</h1>
      <p>{{ text.education.intro }}</p>
    </section>

    <section class="today-section">
      <div v-for="app in learningApps" :key="app.id" class="module-card available education-app-card">
        <span class="module-icon"><img :src="app.icon" alt="" /></span>
        <span>
          <strong>{{ text.education.apps[app.id].title }}</strong>
          <small>{{ text.education.apps[app.id].description }} · {{ text.education.types[app.launch.type] }}</small>
        </span>
        <a
          v-if="app.launch.type === 'standalone' || app.launch.type === 'bot'"
          class="education-open-label"
          :href="app.launch.href"
          target="_blank"
          rel="noopener noreferrer"
        >{{ text.education.open }}</a>
        <RouterLink v-else class="education-open-label" :to="app.launch.route">
          {{ text.education.open }}
        </RouterLink>
      </div>
    </section>
  </MobileShell>
</template>
