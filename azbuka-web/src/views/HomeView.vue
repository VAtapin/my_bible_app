<script setup lang="ts">
import { computed } from 'vue'
import { letters } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'
import { localDateKey } from '@/domain/date'
import { localizedText } from '@/domain/types'
import SlavonicClock from '@/components/SlavonicClock.vue'

const profileStore = useProfileStore()
const { t } = useI18n()
const learnedCount = computed(() => profileStore.profile?.learnedLetterIds.length ?? 0)
const goalProgress = computed(() => {
  const profile = profileStore.profile
  if (!profile) return 0
  const today = localDateKey()
  const answered = profile.todayDate === today ? profile.todayAnswered : 0
  return Math.min(100, (answered / profile.dailyGoal) * 100)
})
const nextLetter = computed(() => letters.find((letter) => !profileStore.profile?.learnedLetterIds.includes(letter.id)) ?? letters[0]!)
</script>

<template>
  <main class="page home-page">
    <header class="topbar">
      <div class="brand-mark"><img src="/icon.svg" alt="" /></div>
      <div><p class="brand-title">{{ t('appName') }}</p><p class="brand-subtitle">{{ t('appSubtitle') }}</p></div>
      <RouterLink to="/profile" class="avatar" :aria-label="t('profile')">{{ profileStore.profile?.locale.toUpperCase() }}</RouterLink>
    </header>

    <section class="hero-card">
      <div class="hero-copy">
        <p class="eyebrow">{{ t('today') }}</p>
        <h1>{{ t('welcome') }}</h1>
        <p>{{ t('welcomeText') }}</p>
        <div class="hero-actions">
          <RouterLink :to="`/alphabet/${nextLetter.id}`" class="primary-button">{{ t('continue') }}</RouterLink>
          <RouterLink to="/practice" class="secondary-button">{{ t('startPractice') }}</RouterLink>
        </div>
      </div>
      <div class="hero-letter" aria-hidden="true">
        <span>{{ nextLetter.glyph }}</span>
        <small>{{ localizedText(nextLetter.name, profileStore.profile?.locale ?? 'cu') }}</small>
      </div>
    </section>

    <SlavonicClock />

    <section class="dashboard-grid">
      <article class="progress-card">
        <div class="section-heading"><div><p class="eyebrow">{{ t('dailyGoal') }}</p><h2>{{ profileStore.profile?.dailyGoal }} {{ t('questions') }}</h2></div><span class="score-ring">{{ Math.round(goalProgress) }}%</span></div>
        <div class="progress-track"><span :style="{ width: `${goalProgress}%` }"></span></div>
      </article>
      <article class="progress-card">
        <p class="eyebrow">{{ t('progress') }}</p>
        <div class="metric"><strong>{{ learnedCount }}</strong><span>/ {{ letters.length }} {{ t('learned') }}</span></div>
        <div class="progress-track gold"><span :style="{ width: `${(learnedCount / letters.length) * 100}%` }"></span></div>
      </article>
    </section>

    <section class="preview-section">
      <div class="section-heading"><div><p class="eyebrow">{{ t('alphabet') }}</p><h2>{{ t('continue') }}</h2></div><RouterLink to="/alphabet" class="text-link">{{ t('learn') }} →</RouterLink></div>
      <div class="letter-preview-row">
        <RouterLink v-for="letter in letters.slice(0, 6)" :key="letter.id" :to="`/alphabet/${letter.id}`" class="mini-letter" :class="{ done: profileStore.profile?.learnedLetterIds.includes(letter.id) }">
          <span>{{ letter.glyph }}</span><small>{{ localizedText(letter.name, profileStore.profile?.locale ?? 'cu') }}</small>
        </RouterLink>
      </div>
    </section>
  </main>
</template>
