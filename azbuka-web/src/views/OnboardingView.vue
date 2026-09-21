<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Locale } from '@/domain/types'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const router = useRouter()
const profileStore = useProfileStore()
const { setLocale, t } = useI18n()
const selectedLocale = ref<Locale>('ru')
const selectedGoal = ref(10)

const chooseLocale = (locale: Locale) => {
  selectedLocale.value = locale
  setLocale(locale)
}

const complete = async () => {
  await profileStore.create(selectedLocale.value, selectedGoal.value)
  await router.replace('/')
}
</script>

<template>
  <main class="onboarding-page">
    <div class="onboarding-art" aria-hidden="true">
      <div class="halo"></div>
      <img src="/icon.svg" alt="" />
      <span class="ornament">☦</span>
    </div>
    <section class="onboarding-card">
      <p class="eyebrow">Bible Desktop</p>
      <h1>{{ t('onboardingTitle') }}</h1>
      <p class="muted">{{ t('onboardingText') }}</p>

      <div class="segmented language-choice" role="group" :aria-label="t('interfaceLanguage')">
        <button :class="{ active: selectedLocale === 'ru' }" @click="chooseLocale('ru')">Русский</button>
        <button :class="{ active: selectedLocale === 'de' }" @click="chooseLocale('de')">Deutsch</button>
      </div>

      <p class="field-label">{{ t('dailyGoal') }}</p>
      <div class="goal-grid">
        <button v-for="goal in [5, 10, 15]" :key="goal" :class="{ selected: selectedGoal === goal }" @click="selectedGoal = goal">
          <strong>{{ goal }}</strong><span>{{ t('questions') }}</span>
        </button>
      </div>

      <button class="primary-button full" @click="complete">{{ t('begin') }}</button>
      <div class="offline-note"><span>✓</span>{{ t('offline') }}</div>
    </section>
  </main>
</template>
