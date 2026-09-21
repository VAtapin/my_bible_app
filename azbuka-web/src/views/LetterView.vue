<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { letterById, letters } from '@/data/letters'
import { letterExampleById } from '@/data/letterExamples'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const route = useRoute()
const router = useRouter()
const profileStore = useProfileStore()
const { t } = useI18n()
const speechMessage = ref('')
const letter = computed(() => letterById(String(route.params.id)))
const example = computed(() => letter.value ? letterExampleById(letter.value.id) : undefined)
const locale = computed(() => profileStore.profile?.locale ?? 'ru')
const isLearned = computed(() => letter.value ? profileStore.profile?.learnedLetterIds.includes(letter.value.id) : false)
const nextLetter = computed(() => {
  const index = letters.findIndex((item) => item.id === letter.value?.id)
  return letters[(index + 1) % letters.length]!
})
const exampleSegments = computed(() => {
  if (!letter.value || !example.value) return []
  const target = letter.value.glyph.toLocaleLowerCase('ru')
  return Array.from(example.value.text).map((character) => ({
    character,
    target: character.toLocaleLowerCase('ru') === target,
  }))
})
let audio: HTMLAudioElement | null = null

const speak = () => {
  if (!example.value) return
  speechMessage.value = ''
  window.speechSynthesis?.cancel()
  audio?.pause()

  if (example.value.audioSrc) {
    audio = new Audio(example.value.audioSrc)
    void audio.play()
    return
  }

  if (!('speechSynthesis' in window)) {
    speechMessage.value = t('speechUnavailable')
    return
  }
  const utterance = new SpeechSynthesisUtterance(example.value.text)
  utterance.lang = 'ru-RU'
  window.speechSynthesis.speak(utterance)
  speechMessage.value = t('systemVoiceNotice')
}

onBeforeUnmount(() => {
  window.speechSynthesis?.cancel()
  audio?.pause()
})
</script>

<template>
  <main v-if="letter" class="page letter-page">
    <button class="back-button" @click="router.back()"><AppIcon name="arrow" />{{ t('back') }}</button>
    <section class="letter-hero">
      <div class="large-glyph" :aria-label="letter.name[locale]">{{ letter.glyph }}</div>
      <div class="letter-summary">
        <p class="eyebrow">{{ t('letterName') }}</p>
        <h1>{{ letter.name[locale] }}</h1>
        <p>{{ letter.meaning[locale] }}</p>
        <button class="sound-button" @click="speak"><AppIcon name="sound" />{{ t('audioExample') }}</button>
        <small v-if="speechMessage" class="muted">{{ speechMessage }}</small>
      </div>
    </section>
    <section v-if="example" class="letter-example card">
      <div class="example-heading">
        <div><p class="eyebrow">{{ t('exampleWithLetter') }} {{ letter.glyph }}</p><h2>{{ t('readInContext') }}</h2></div>
        <span>{{ example.source[locale] }}</span>
      </div>
      <blockquote lang="cu">
        <template v-for="(segment, segmentIndex) in exampleSegments" :key="segmentIndex"><mark v-if="segment.target">{{ segment.character }}</mark><template v-else>{{ segment.character }}</template></template>
      </blockquote>
      <p class="example-translation">{{ example.translation[locale] }}</p>
      <small>{{ t('exampleCorpus') }}</small>
    </section>
    <section class="facts-grid">
      <article><p>{{ t('transliteration') }}</p><strong>{{ letter.transliteration }}</strong></article>
      <article><p>{{ t('number') }}</p><strong>{{ letter.numericValue ?? t('noNumber') }}</strong></article>
      <article class="wide"><p>{{ t('meaning') }}</p><strong>{{ letter.meaning[locale] }}</strong></article>
    </section>
    <div class="letter-actions">
      <button class="primary-button" :class="{ completed: isLearned }" @click="profileStore.markLearned(letter.id)">
        <AppIcon v-if="isLearned" name="check" />{{ isLearned ? t('learnedDone') : t('markLearned') }}
      </button>
      <RouterLink :to="`/alphabet/${nextLetter.id}`" class="secondary-button">{{ t('nextLetter') }} →</RouterLink>
    </div>
  </main>
  <main v-else class="page"><RouterLink to="/alphabet" class="primary-button">{{ t('back') }}</RouterLink></main>
</template>
