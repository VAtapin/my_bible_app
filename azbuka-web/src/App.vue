<script setup lang="ts">
import { azbukaPath, azbukaIcon } from './integration'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNavigation from './components/BottomNavigation.vue'
import { useI18n } from './i18n'
import { useProfileStore } from './stores/profile'

const route = useRoute()
const router = useRouter()
const profileStore = useProfileStore()
const { t, locale } = useI18n()

onMounted(async () => {
  await profileStore.initialize()
  if (!profileStore.profile && route.name !== 'onboarding') {
    await router.replace(azbukaPath('/onboarding'))
  } else if (profileStore.profile && route.name === 'onboarding') {
    await router.replace(azbukaPath('/'))
  }
})
</script>

<template>
  <div class="azbuka-app" :data-locale="locale">
  <div v-if="!profileStore.initialized" class="loading-screen">
    <img :src="azbukaIcon()" alt="" />
    <p>{{ t('loading') }}</p>
  </div>
  <RouterView v-else />
  <BottomNavigation v-if="profileStore.profile && route.name !== 'onboarding' && route.name !== 'practice-session'" />
  </div>
</template>
