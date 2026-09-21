<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { createSession } from '@/domain/exerciseEngine'
import { applyAnswer, initialAnswerState } from '@/domain/practiceFlow'
import { letterById } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const router = useRouter()
const profileStore = useProfileStore()
const { t } = useI18n()
const locale = computed(() => profileStore.profile?.locale ?? 'ru')
const questions = ref(createSession(locale.value, profileStore.profile?.dailyGoal ?? 10))
const index = ref(0)
const answerState = ref(initialAnswerState())
const correctCount = ref(0)
const sessionStreak = ref(0)
const bestSessionStreak = ref(0)
const points = ref(0)
const earnedPoints = ref(0)
const finished = ref(false)
const saving = ref(false)
let pendingSave: Promise<void> = Promise.resolve()
let advanceTimer: number | null = null
const current = computed(() => questions.value[index.value]!)
const currentLetter = computed(() => letterById(current.value.letterId)!)
const resolved = computed(() => answerState.value.phase === 'resolved')
const feedbackVisible = computed(() => answerState.value.phase !== 'idle')
const corrected = computed(() => resolved.value && answerState.value.firstTryCorrect === false)
const instruction = computed(() => current.value.kind === 'glyph-to-name' ? t('chooseName') : current.value.kind === 'name-to-glyph' ? t('chooseGlyph') : t('chooseNumber'))
const successMessage = computed(() => {
  if (corrected.value) return t('corrected')
  if (sessionStreak.value >= 4) return t('excellent')
  if (sessionStreak.value >= 2) return t('great')
  return t('correct')
})
const optionAriaLabel = (optionId: string, visibleLabel: string) => {
  if (current.value.kind !== 'name-to-glyph') return visibleLabel
  const optionLetter = letterById(optionId)
  return optionLetter ? `${optionLetter.name[locale.value]} — ${visibleLabel}` : visibleLabel
}

const clearAdvanceTimer = () => {
  if (advanceTimer !== null) window.clearTimeout(advanceTimer)
  advanceTimer = null
}

const vibrate = (pattern: number | number[]) => {
  if ('vibrate' in navigator) navigator.vibrate(pattern)
}

const recordFirstAnswer = (isCorrect: boolean) => {
  saving.value = true
  pendingSave = profileStore.recordAnswer(isCorrect).finally(() => {
    saving.value = false
  })
}

const scheduleAdvance = () => {
  clearAdvanceTimer()
  advanceTimer = window.setTimeout(() => void advance(), 1800)
}

const select = (optionId: string) => {
  const transition = applyAnswer(answerState.value, optionId, current.value.correctOptionId)
  if (transition.event === 'ignored') return
  answerState.value = transition.state

  if (transition.recordResult !== null) recordFirstAnswer(transition.recordResult)

  if (transition.event === 'wrong') {
    sessionStreak.value = 0
    earnedPoints.value = 0
    vibrate([55, 45, 55])
    return
  }

  if (transition.event === 'correct') {
    sessionStreak.value += 1
    bestSessionStreak.value = Math.max(bestSessionStreak.value, sessionStreak.value)
    earnedPoints.value = 10 + Math.min(20, (sessionStreak.value - 1) * 2)
    points.value += earnedPoints.value
    correctCount.value += 1
  } else {
    earnedPoints.value = 0
  }

  vibrate(35)
  scheduleAdvance()
}

const advance = async () => {
  if (!resolved.value) return
  clearAdvanceTimer()
  await pendingSave
  if (index.value === questions.value.length - 1) {
    finished.value = true
    return
  }
  index.value += 1
  answerState.value = initialAnswerState()
  earnedPoints.value = 0
}

const restart = () => {
  questions.value = createSession(locale.value, profileStore.profile?.dailyGoal ?? 10)
  index.value = 0
  answerState.value = initialAnswerState()
  correctCount.value = 0
  sessionStreak.value = 0
  bestSessionStreak.value = 0
  points.value = 0
  earnedPoints.value = 0
  finished.value = false
}

onBeforeUnmount(clearAdvanceTimer)
</script>

<template>
  <main class="session-page">
    <template v-if="!finished">
      <header class="session-header">
        <button class="icon-button" :aria-label="t('back')" @click="router.push('/practice')"><AppIcon name="arrow" /></button>
        <div class="session-progress"><span :style="{ width: `${((index + (resolved ? 1 : 0)) / questions.length) * 100}%` }"></span></div>
        <div class="session-scoreboard">
          <span :class="{ active: sessionStreak > 1 }" :title="t('streakLabel')">✦ {{ sessionStreak }}</span>
          <strong>{{ points }} <small>{{ t('pointsLabel') }}</small></strong>
          <b>{{ index + 1 }}/{{ questions.length }}</b>
        </div>
      </header>
      <Transition name="question" mode="out-in">
        <section :key="current.id" class="question-card" :class="{ 'is-wrong': answerState.phase === 'wrong' }">
          <p class="eyebrow">{{ instruction }}</p>
          <div class="question-prompt" :class="{ glyph: current.kind !== 'name-to-glyph' }">{{ current.prompt }}</div>
          <div class="answer-grid" :class="{ glyphs: current.kind === 'name-to-glyph' }">
            <button
              v-for="option in current.options"
              :key="option.id"
              :aria-label="optionAriaLabel(option.id, option.label)"
              :class="{
                correct: feedbackVisible && option.id === current.correctOptionId,
                wrong: answerState.wrongOptionId === option.id,
                waiting: answerState.phase === 'wrong' && option.id === current.correctOptionId,
                locked: resolved && option.id !== current.correctOptionId,
              }"
              @click="select(option.id)"
            >
              <span>{{ option.label }}</span>
              <b v-if="feedbackVisible && option.id === current.correctOptionId" class="answer-mark">✓</b>
              <b v-else-if="answerState.wrongOptionId === option.id" class="answer-mark">×</b>
            </button>
          </div>
        </section>
      </Transition>
      <div v-if="resolved" class="success-burst" aria-hidden="true"><i v-for="particle in 8" :key="particle"></i></div>
      <footer v-if="feedbackVisible" class="answer-feedback" :class="resolved ? 'positive' : 'negative'" role="status" aria-live="polite">
        <div>
          <strong>{{ resolved ? successMessage : t('wrong') }}</strong>
          <span v-if="answerState.phase === 'wrong'">{{ t('tapCorrect') }}: {{ currentLetter.name[locale] }} — {{ currentLetter.glyph }}</span>
          <span v-else-if="earnedPoints">+{{ earnedPoints }} {{ t('pointsLabel') }} · {{ t('autoNext') }}</span>
          <span v-else>{{ t('correctedHint') }} · {{ t('autoNext') }}</span>
        </div>
        <button v-if="resolved" class="primary-button" :disabled="saving" @click="advance">{{ index === questions.length - 1 ? t('finish') : t('next') }}</button>
        <span v-else class="correction-cue">↑ {{ t('chooseHighlighted') }}</span>
        <span v-if="resolved" class="auto-advance-line" aria-hidden="true"></span>
      </footer>
    </template>
    <section v-else class="result-card">
      <div class="result-medal">{{ correctCount === questions.length ? '✦' : '✓' }}</div>
      <p class="eyebrow">{{ t('practice') }}</p>
      <h1>{{ t('resultTitle') }}</h1>
      <p>{{ t('resultText') }}</p>
      <strong class="result-score">{{ correctCount }} / {{ questions.length }}</strong>
      <div class="result-badges"><span>✦ {{ points }} {{ t('pointsLabel') }}</span><span>↗ {{ t('bestStreak') }}: {{ bestSessionStreak }}</span></div>
      <div class="result-actions"><button class="primary-button" @click="restart">{{ t('retry') }}</button><button class="secondary-button" @click="router.push('/')">{{ t('home') }}</button></div>
    </section>
  </main>
</template>
