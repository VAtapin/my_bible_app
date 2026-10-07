<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { educationSettings } from '@/profile/configuration'
import { learningApps } from './learningApps'

const { messages: text } = useI18n()
const profile = useProfileStore()
const apps = computed(() => learningApps.filter((app) => educationSettings(profile.configuration).pluginIds.includes(app.id)))
onMounted(() => profile.load())
</script>

<template>
  <MobileShell back-to="/today">
    <header class="section-heading-row">
      <h1>{{ text.education.title }}</h1>
      <RouterLink to="/setup/manual?edit=1">{{ text.today.customize }}</RouterLink>
    </header>
    <div class="module-list">
      <RouterLink v-for="app in apps" :key="app.id" class="module-card available" :to="app.route">
        <span class="module-icon"><img :src="app.icon" alt="" /></span>
        <span><strong>{{ text.education.apps[app.id].title }}</strong><small>{{ text.education.apps[app.id].description }}</small></span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <p v-if="!apps.length">{{ text.education.empty }}</p>
    </div>
  </MobileShell>
</template>
