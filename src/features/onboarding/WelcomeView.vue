<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'

const router = useRouter()
const profile = useProfileStore()
const { messages: text } = useI18n()

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
      <p class="eyebrow">{{ text.welcome.eyebrow }}</p>
      <h1>{{ text.welcome.title }}</h1>
      <p>{{ text.welcome.intro }}</p>
    </section>

    <section class="welcome-actions" :aria-label="text.welcome.startLabel">
      <RouterLink class="choice-card primary-choice" to="/setup/quick">
        <span class="choice-number">01</span>
        <span>
          <strong>{{ text.welcome.quickTitle }}</strong>
          <small>{{ text.welcome.quickDescription }}</small>
        </span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink class="choice-card" to="/setup/manual">
        <span class="choice-number">02</span>
        <span>
          <strong>{{ text.welcome.manualTitle }}</strong>
          <small>{{ text.welcome.manualDescription }}</small>
        </span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink class="restore-link" to="/restore">{{ text.welcome.restore }}</RouterLink>
    </section>

    <p class="reassurance">{{ text.welcome.reassurance }}</p>
    <RouterLink class="privacy-link" to="/privacy">{{ text.welcome.privacy }}</RouterLink>
  </MobileShell>
</template>
