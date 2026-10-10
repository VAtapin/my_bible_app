<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/i18n'
import { readerControlMessages } from '@/i18n/readerControls'
import type { ReaderHistoryState } from '@/services/readerHistory'
defineProps<{state: ReaderHistoryState; canBack: boolean; canForward: boolean}>()
const emit = defineEmits<{back: []; forward: []; select: [number]; close: []}>()
const {language} = useI18n(), text = computed(() => readerControlMessages[language.value])
</script>
<template><section class="reader-history" role="dialog" :aria-label="text.history" data-no-reader-gesture><header><h2>{{ text.history }}</h2><button @click="emit('close')">{{ text.close }}</button></header><button :disabled="!canBack" @click="emit('back')">{{ text.back }}</button> <button :disabled="!canForward" @click="emit('forward')">{{ text.forward }}</button><ol><li v-for="(place,index) in state.entries" :key="index"><button :aria-current="index === state.cursor ? 'location' : undefined" @click="emit('select',index)">{{ place.code }} · {{ place.book }} {{ place.chapter }}:{{ place.verse || 1 }}</button></li></ol></section></template>
<style scoped>.reader-history{position:fixed;inset:4dvh max(1rem,calc((100vw - 38rem)/2));overflow:auto;background:var(--surface,#fff);padding:1rem;z-index:100;border:1px solid #aaa;border-radius:1rem;box-shadow:0 0 0 100vmax #0007}.reader-history header{display:flex;align-items:center;justify-content:space-between}.reader-history li{margin:.5rem 0}.reader-history [aria-current]{font-weight:bold}</style>
