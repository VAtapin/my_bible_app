<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNavigation from '@/components/BottomNavigation.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const route = useRoute()
const router = useRouter()
const profileStore = useProfileStore()
const { t } = useI18n()

onMounted(async () => {
  await profileStore.initialize()
  if (!profileStore.profile && route.name !== 'onboarding') {
    await router.replace('/onboarding')
  } else if (profileStore.profile && route.name === 'onboarding') {
    await router.replace('/')
  }
})
</script>

<template>
  <div v-if="!profileStore.initialized" class="loading-screen">
    <img src="/icon.svg" alt="" />
    <p>{{ t('loading') }}</p>
  </div>
  <RouterView v-else />
  <BottomNavigation v-if="profileStore.profile && route.name !== 'onboarding' && route.name !== 'practice-session'" />
</template>
