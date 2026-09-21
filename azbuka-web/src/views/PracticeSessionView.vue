<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { createSession, isCorrectAnswer } from '@/domain/exerciseEngine'
import { letterById } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const router = useRouter()
const profileStore = useProfileStore()
const { t } = useI18n()
const locale = computed(() => profileStore.profile?.locale ?? 'ru')
const questions = ref(createSession(locale.value, profileStore.profile?.dailyGoal ?? 10))
const index = ref(0)
const selectedOptionId = ref<string | null>(null)
const correctCount = ref(0)
const finished = ref(false)
const saving = ref(false)
const current = computed(() => questions.value[index.value]!)
const currentLetter = computed(() => letterById(current.value.letterId)!)
const answered = computed(() => selectedOptionId.value !== null)
const selectedIsCorrect = computed(() => selectedOptionId.value ? isCorrectAnswer(current.value, selectedOptionId.value) : false)
const instruction = computed(() => current.value.kind === 'glyph-to-name' ? t('chooseName') : current.value.kind === 'name-to-glyph' ? t('chooseGlyph') : t('chooseNumber'))
const optionAriaLabel = (optionId: string, visibleLabel: string) => {
  if (current.value.kind !== 'name-to-glyph') return visibleLabel
  const optionLetter = letterById(optionId)
  return optionLetter ? `${optionLetter.name[locale.value]} — ${visibleLabel}` : visibleLabel
}

const select = async (optionId: string) => {
  if (answered.value) return
  selectedOptionId.value = optionId
  const isCorrect = isCorrectAnswer(current.value, optionId)
  if (isCorrect) correctCount.value += 1
  saving.value = true
  await profileStore.recordAnswer(isCorrect)
  saving.value = false
}

const advance = async () => {
  if (index.value === questions.value.length - 1) {
    finished.value = true
    return
  }
  index.value += 1
  selectedOptionId.value = null
}

const restart = () => {
  questions.value = createSession(locale.value, profileStore.profile?.dailyGoal ?? 10)
  index.value = 0
  selectedOptionId.value = null
  correctCount.value = 0
  finished.value = false
}
</script>

<template>
  <main class="session-page">
    <template v-if="!finished">
      <header class="session-header">
        <button class="icon-button" :aria-label="t('back')" @click="router.push('/practice')"><AppIcon name="arrow" /></button>
        <div class="session-progress"><span :style="{ width: `${((index + (answered ? 1 : 0)) / questions.length) * 100}%` }"></span></div>
        <strong>{{ index + 1 }}/{{ questions.length }}</strong>
      </header>
      <section class="question-card">
        <p class="eyebrow">{{ instruction }}</p>
        <div class="question-prompt" :class="{ glyph: current.kind !== 'name-to-glyph' }">{{ current.prompt }}</div>
        <div class="answer-grid" :class="{ glyphs: current.kind === 'name-to-glyph' }">
          <button v-for="option in current.options" :key="option.id" :aria-label="optionAriaLabel(option.id, option.label)" :class="{ selected: selectedOptionId === option.id, correct: answered && option.id === current.correctOptionId, wrong: selectedOptionId === option.id && !selectedIsCorrect }" @click="select(option.id)">
            {{ option.label }}
          </button>
        </div>
      </section>
      <footer v-if="answered" class="answer-feedback" :class="selectedIsCorrect ? 'positive' : 'negative'" role="status" aria-live="polite">
        <div><strong>{{ selectedIsCorrect ? t('correct') : t('wrong') }}</strong><span v-if="!selectedIsCorrect">{{ currentLetter.name[locale] }} — {{ currentLetter.glyph }}</span></div>
        <button class="primary-button" :disabled="saving" @click="advance">{{ index === questions.length - 1 ? t('finish') : t('next') }}</button>
      </footer>
    </template>
    <section v-else class="result-card">
      <div class="result-medal">{{ correctCount === questions.length ? '✦' : '✓' }}</div>
      <p class="eyebrow">{{ t('practice') }}</p>
      <h1>{{ t('resultTitle') }}</h1>
      <p>{{ t('resultText') }}</p>
      <strong class="result-score">{{ correctCount }} / {{ questions.length }}</strong>
      <div class="result-actions"><button class="primary-button" @click="restart">{{ t('retry') }}</button><button class="secondary-button" @click="router.push('/')">{{ t('home') }}</button></div>
    </section>
  </main>
</template>
