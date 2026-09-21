<script setup lang="ts">
import { computed, ref } from 'vue'
import { letters } from '@/data/letters'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profile'

const { t } = useI18n()
const profileStore = useProfileStore()
const filter = ref<'all' | 'basic' | 'historic' | 'numeral'>('all')
const visibleLetters = computed(() => filter.value === 'all' ? letters : letters.filter((letter) => letter.group === filter.value || (filter.value === 'numeral' && letter.numericValue !== null)))
const locale = computed(() => profileStore.profile?.locale ?? 'ru')
</script>

<template>
  <main class="page">
    <header class="page-header">
      <p class="eyebrow">{{ t('learn') }}</p>
      <h1>{{ t('alphabet') }}</h1>
      <p>{{ t('alphabetIntro') }}</p>
    </header>
    <div class="chip-row" role="tablist">
      <button :class="{ active: filter === 'all' }" @click="filter = 'all'">{{ locale === 'ru' ? 'Все' : 'Alle' }}</button>
      <button :class="{ active: filter === 'basic' }" @click="filter = 'basic'">{{ locale === 'ru' ? 'Основные' : 'Grundzeichen' }}</button>
      <button :class="{ active: filter === 'historic' }" @click="filter = 'historic'">{{ locale === 'ru' ? 'Исторические' : 'Historisch' }}</button>
      <button :class="{ active: filter === 'numeral' }" @click="filter = 'numeral'">{{ locale === 'ru' ? 'Цифирь' : 'Zahlen' }}</button>
    </div>
    <section class="alphabet-grid">
      <RouterLink v-for="letter in visibleLetters" :key="letter.id" :to="`/alphabet/${letter.id}`" class="letter-tile" :class="{ learned: profileStore.profile?.learnedLetterIds.includes(letter.id) }" :aria-label="`${letter.name[locale]} — ${letter.glyph}${letter.numericValue !== null ? `, ${letter.numericValue}` : ''}`">
        <span class="tile-check" v-if="profileStore.profile?.learnedLetterIds.includes(letter.id)">✓</span>
        <strong>{{ letter.glyph }}</strong>
        <span>{{ letter.name[locale] }}</span>
        <small v-if="letter.numericValue !== null">{{ letter.numericValue }}</small>
      </RouterLink>
    </section>
  </main>
</template>
