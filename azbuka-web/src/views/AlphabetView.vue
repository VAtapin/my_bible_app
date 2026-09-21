<script setup lang="ts">
import { computed, ref } from 'vue'
import { letters } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'
import { localizedText } from '@/domain/types'

const { t } = useI18n()
const profileStore = useProfileStore()
const filter = ref<'all' | 'basic' | 'historic'>('all')
const visibleLetters = computed(() => filter.value === 'all' ? letters : letters.filter((letter) => letter.group === filter.value))
const locale = computed(() => profileStore.profile?.locale ?? 'cu')
</script>

<template>
  <main class="page">
    <header class="page-header">
      <p class="eyebrow">{{ t('learn') }}</p>
      <h1>{{ t('alphabet') }}</h1>
      <p>{{ t('alphabetIntro') }}</p>
    </header>
    <div class="chip-row" role="tablist">
      <button :class="{ active: filter === 'all' }" @click="filter = 'all'">{{ t('filterAll') }}</button>
      <button :class="{ active: filter === 'basic' }" @click="filter = 'basic'">{{ t('filterBasic') }}</button>
      <button :class="{ active: filter === 'historic' }" @click="filter = 'historic'">{{ t('filterHistoric') }}</button>
    </div>
    <section class="alphabet-grid">
      <RouterLink v-for="letter in visibleLetters" :key="letter.id" :to="`/alphabet/${letter.id}`" class="letter-tile" :class="{ learned: profileStore.profile?.learnedLetterIds.includes(letter.id) }" :aria-label="`${localizedText(letter.name, locale)} — ${letter.glyph}`">
        <span class="tile-check" v-if="profileStore.profile?.learnedLetterIds.includes(letter.id)">✓</span>
        <strong>{{ letter.glyph }}</strong>
        <span>{{ localizedText(letter.name, locale) }}</span>
      </RouterLink>
    </section>
  </main>
</template>
