<script setup lang="ts">
import { ref } from 'vue'
import type { Locale } from '@/domain/types'
import { letters } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const { t } = useI18n()
const profileStore = useProfileStore()
const resetArmed = ref(false)

const setLanguage = async (locale: Locale) => {
  await profileStore.changeLocale(locale)
}

const resetProgress = async () => {
  if (!resetArmed.value) {
    resetArmed.value = true
    window.setTimeout(() => { resetArmed.value = false }, 4000)
    return
  }
  await profileStore.reset()
  resetArmed.value = false
}
</script>

<template>
  <main class="page profile-page">
    <header class="page-header"><p class="eyebrow">Bible Desktop</p><h1>{{ t('settings') }}</h1></header>
    <section class="stat-grid">
      <article><strong>{{ profileStore.profile?.learnedLetterIds.length ?? 0 }}</strong><span>{{ t('mastered') }} / {{ letters.length }}</span></article>
      <article><strong>{{ profileStore.accuracy }}%</strong><span>{{ t('accuracy') }}</span></article>
      <article><strong>{{ profileStore.profile?.streak ?? 0 }}</strong><span>{{ t('streak') }}</span></article>
    </section>
    <section class="settings-card card">
      <div class="setting-block">
        <label>{{ t('interfaceLanguage') }}</label>
        <div class="segmented"><button :class="{ active: profileStore.profile?.locale === 'ru' }" @click="setLanguage('ru')">Русский</button><button :class="{ active: profileStore.profile?.locale === 'de' }" @click="setLanguage('de')">Deutsch</button></div>
      </div>
      <div class="setting-block">
        <label>{{ t('goal') }}</label>
        <div class="segmented"><button v-for="goal in [5, 10, 15]" :key="goal" :class="{ active: profileStore.profile?.dailyGoal === goal }" @click="profileStore.changeGoal(goal)">{{ goal }}</button></div>
      </div>
      <div class="install-banner"><img src="/icon.svg" alt="" /><div><strong>{{ t('install') }}</strong><span>{{ t('installText') }}</span></div></div>
      <button class="danger-button" @click="resetProgress">{{ resetArmed ? t('resetConfirm') : t('reset') }}</button>
    </section>
  </main>
</template>
