<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/i18n'
import { readerControlMessages } from '@/i18n/readerControls'
import { useReaderPreferences, type ReaderPreferences } from '@/profile/readerPreferences'
import {useReferencePreferences}from'@/profile/referencePreferences'
import {inlineReferenceMessages}from'@/i18n/inlineReferences'
const emit = defineEmits<{close: []}>()
const { language } = useI18n(), text = computed(() => readerControlMessages[language.value])
const { preferences, initialize, setPreferences } = useReaderPreferences(); initialize()
const referencePreferences=useReferencePreferences();referencePreferences.initialize()
const referenceText=computed(()=>inlineReferenceMessages[language.value])
const switches = ['chapterLabels','verseNumbers','separateVerses','headings','crossReferences','commentaryLinks','footnotes','strongNumbers','paragraphs','addedWords','clean','night','tapPaging','swipeChapters','swipeBooks'] as const
function change(key: keyof ReaderPreferences, event: Event) { const input = event.target as HTMLInputElement; setPreferences({...preferences.value, [key]: input.type === 'checkbox' ? input.checked : Number(input.value)}) }
</script>
<template>
  <section class="reader-settings" role="dialog" aria-modal="true" :aria-label="text.settings" data-no-reader-gesture>
    <header><h2>{{ text.settings }}</h2><button @click="emit('close')">{{ text.close }}</button></header>
    <label>{{ text.fontSize }} {{ preferences.fontSize }} <input type="range" min="14" max="36" :value="preferences.fontSize" @input="change('fontSize',$event)" /></label>
    <label>{{ text.lineHeight }} {{ preferences.lineHeight }} <input type="range" min="1.2" max="2.2" step=".05" :value="preferences.lineHeight" @input="change('lineHeight',$event)" /></label>
    <label v-for="key in switches" :key="key"><input type="checkbox" :checked="preferences[key]" @change="change(key,$event)" /> {{ text[key] }}</label>
    <label>{{text.crossReferences}}<select :value="referencePreferences.settings.value.inlineMode" @change="referencePreferences.setSettings({...referencePreferences.settings.value,inlineMode:($event.target as HTMLSelectElement).value==='list'?'list':'compact'})"><option value="compact">{{referenceText.compact}}</option><option value="list">{{referenceText.list}}</option></select></label>
    <p>{{ text.available }}</p>
  </section>
</template>
<style scoped>.reader-settings{position:fixed;inset:4dvh max(1rem,calc((100vw - 38rem)/2));z-index:100;overflow:auto;padding:1rem;background:var(--surface,#fff);color:var(--text-color,#17324a);border:1px solid #aaa;border-radius:1rem;box-shadow:0 0 0 100vmax #0007}.reader-settings header{display:flex;justify-content:space-between;gap:1rem;align-items:center}.reader-settings label{display:block;padding:.55rem 0}.reader-settings input[type=range]{display:block;width:100%}</style>
