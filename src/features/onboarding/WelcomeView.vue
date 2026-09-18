<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { ru } from '@/i18n/ru'
import { useProfileStore } from '@/stores/profileStore'

const router = useRouter()
const profile = useProfileStore()

onMounted(() => {
  if (profile.load()) {
    void router.replace('/today')
  }
})
</script>

<template>
  <MobileShell :show-navigation="false">
    <section class="welcome-hero">
      <img src="/brand/app-icon-512.png" alt="" />
      <p class="eyebrow">{{ ru.welcome.eyebrow }}</p>
      <h1>{{ ru.welcome.title }}</h1>
      <p>{{ ru.welcome.intro }}</p>
    </section>

    <section class="welcome-actions" :aria-label="ru.welcome.startLabel">
      <RouterLink class="choice-card primary-choice" to="/setup/quick">
        <span class="choice-number">01</span>
        <span>
          <strong>{{ ru.welcome.quickTitle }}</strong>
          <small>{{ ru.welcome.quickDescription }}</small>
        </span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink class="choice-card" to="/setup/manual">
        <span class="choice-number">02</span>
        <span>
          <strong>{{ ru.welcome.manualTitle }}</strong>
          <small>{{ ru.welcome.manualDescription }}</small>
        </span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink class="restore-link" to="/restore">{{ ru.welcome.restore }}</RouterLink>
    </section>

    <p class="reassurance">{{ ru.welcome.reassurance }}</p>
    <RouterLink class="privacy-link" to="/privacy">Конфиденциальность</RouterLink>
  </MobileShell>
</template>
