<script setup lang="ts">
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const { t } = useI18n()
const profileStore = useProfileStore()
</script>

<template>
  <main class="page practice-page">
    <header class="page-header centered">
      <div class="practice-emblem"><span>а</span><small>1</small></div>
      <p class="eyebrow">{{ t('practice') }}</p>
      <h1>{{ t('practiceTitle') }}</h1>
      <p>{{ t('practiceIntro') }}</p>
    </header>
    <section class="practice-setup card">
      <div class="setup-row"><div><strong>{{ t('namesMode') }}</strong><span>{{ t('signAndName') }}</span></div><span class="status-dot active">✓</span></div>
      <div class="setup-row"><div><strong>{{ profileStore.profile?.dailyGoal }} {{ t('questions') }}</strong><span>{{ t('noTimeLimit') }}</span></div><span class="status-dot">∞</span></div>
      <RouterLink to="/practice/session" class="primary-button full">{{ t('start') }}</RouterLink>
    </section>
  </main>
</template>
