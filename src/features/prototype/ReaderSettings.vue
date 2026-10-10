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
    <label class="slider-row"><span>{{ text.fontSize }} {{ preferences.fontSize }}</span><input type="range" min="14" max="36" :value="preferences.fontSize" @input="change('fontSize',$event)" /></label>
    <label class="slider-row"><span>{{ text.lineHeight }} {{ preferences.lineHeight }}</span><input type="range" min="1.2" max="2.2" step=".05" :value="preferences.lineHeight" @input="change('lineHeight',$event)" /></label>
    <div class="switch-grid"><label v-for="key in switches" :key="key" class="switch-row"><input type="checkbox" :checked="preferences[key]" @change="change(key,$event)" /><span>{{ text[key] }}</span></label></div>
    <label class="mode-row"><span>{{text.crossReferences}}</span><select :value="referencePreferences.settings.value.inlineMode" @change="referencePreferences.setSettings({...referencePreferences.settings.value,inlineMode:($event.target as HTMLSelectElement).value==='list'?'list':'compact'})"><option value="compact">{{referenceText.compact}}</option><option value="list">{{referenceText.list}}</option></select></label>
    <p>{{ text.available }}</p>
  </section>
</template>
<style scoped>
.reader-settings{position:fixed;inset:4dvh max(1rem,calc((100vw - 38rem)/2));z-index:100;overflow:auto;padding:1rem;background:var(--surface,#fff);color:var(--text-color,#17324a);border:1px solid #aaa;border-radius:1rem;box-shadow:0 0 0 100vmax #0007}
.reader-settings header{display:flex;justify-content:space-between;gap:1rem;align-items:center}.reader-settings h2{margin:0 0 .5rem;font-size:1.2rem}
.reader-settings label{margin:0;min-height:44px;cursor:pointer}.reader-settings label span{display:block;margin:0;color:inherit;font:inherit}
.switch-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:0 12px;margin:6px 0}
.switch-row{display:flex;align-items:center;gap:10px;padding:4px 0}.switch-row input[type=checkbox]{width:18px;height:18px;min-height:0;flex:0 0 18px;margin:0;padding:0;border-radius:3px;accent-color:var(--brand-blue)}
.slider-row,.mode-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(120px,1fr);align-items:center;gap:12px;padding:4px 0}
.slider-row input[type=range]{width:100%;min-width:0;min-height:24px;height:24px;padding:0;margin:0;background:transparent}.mode-row select{min-height:40px}
@media(min-width:640px){.switch-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
