<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref} from 'vue'
import type {CalendarReading} from '@/api/contracts'
import type {CalendarTextMetadata} from '@/api/automaticCalendar'
import {bibleApi} from '@/api'
import {useI18n} from '@/i18n'
import {calendarReaderMessages} from '@/i18n/calendarReader'
import {createChapterService} from '@/services/chapterService'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {loadCalendarReading,parsedCalendarPassages,type CalendarReadingPart} from '@/services/calendarReading'
import {calendarReaderBack} from '@/services/calendarReaderBack'
import {useReaderPreferences,effectiveReaderPreferences} from '@/profile/readerPreferences'
import SourceVerseText from '@/features/prototype/SourceVerseText.vue'
import CalendarServiceText from './CalendarServiceText.vue'
import {normalizePrayerText} from '@/services/prayerContent'
const props=defineProps<{reading?:CalendarReading;item?:CalendarTextMetadata&{title:string;text:string;rubric?:string|null};code?:string}>(),emit=defineEmits<{close:[]}>()
const {language,messages}=useI18n(),labels=computed(()=>calendarReaderMessages[language.value])
const modal=ref<HTMLDialogElement>(),parts=ref<CalendarReadingPart[]>(),failed=ref(false),loading=ref(false)
const title=computed(()=>props.reading?.display_ref||props.reading?.title||props.item?.title||'')
const reader=useReaderPreferences();reader.initialize();const preferences=computed(()=>effectiveReaderPreferences(reader.preferences.value))
let disposed=false,back:ReturnType<typeof calendarReaderBack>|undefined,opener:HTMLElement|undefined
const controller=new AbortController()
function close(){back?.close()}
onMounted(async()=>{
 opener=document.activeElement instanceof HTMLElement?document.activeElement:undefined
 back=calendarReaderBack(()=>emit('close'));modal.value?.showModal()
 if(!props.reading)return
 if(!parsedCalendarPassages(props.reading)){failed.value=true;return}
 loading.value=true
 try{const loaded=await loadCalendarReading(props.reading,props.code??'',createChapterService(bibleApi,createIndexedDbChapterRepository()),undefined,undefined,controller.signal);if(!disposed)parts.value=loaded}
 catch{if(!disposed)failed.value=true}
 finally{if(!disposed)loading.value=false}
})
onUnmounted(()=>{disposed=true;controller.abort();back?.dispose();opener?.focus({preventScroll:true})})
</script>
<template><Teleport to="body"><dialog ref="modal" class="calendar-text-reader" :aria-label="title" @cancel.prevent="close">
 <header><h2>{{title}}</h2><button autofocus type="button" @click="close">{{labels.close}}</button></header>
 <div class="calendar-text-body" :class="{'reader-night':preferences.night}" :style="{fontSize:`${preferences.fontSize}px`,lineHeight:String(preferences.lineHeight)}">
   <p v-if="loading" role="status">{{messages.loading}}</p>
   <p v-if="failed" role="status">{{reading&&!parsedCalendarPassages(reading)?labels.unparsed:labels.failed}}</p>
   <template v-for="(part,index) in parts" :key="index"><section v-for="chapter in part.chapters" :key="`${chapter.translation.code}:${chapter.book.slug}:${chapter.chapter.number}`" :lang="chapter.translation.language.code" :class="{'slavonic-unicode':chapter.translation.language.code==='cu','slavonic-civil':chapter.translation.language.code==='cu-civil'}">
     <h3>{{chapter.book.name}} {{chapter.chapter.number}} · {{chapter.translation.name}}</h3>
     <p v-for="verse in chapter.verses" :key="verse.osis_ref" class="calendar-verse"><small v-if="preferences.verseNumbers">{{verse.number}} </small><SourceVerseText :verse="verse" :code="chapter.translation.code" :preferences="preferences" :marks="[]" read-only/></p>
   </section></template>
   <template v-if="item"><p v-if="item.rubric">{{normalizePrayerText(item.rubric)}}</p><CalendarServiceText :item="item"/></template>
 </div>
</dialog></Teleport></template>
<style scoped>
.calendar-text-reader{position:fixed;inset:0;width:100vw;height:100dvh;max-width:none;max-height:none;margin:0;padding:0;border:0;border-radius:0;background:var(--white,#fff);color:var(--ink,#17324a)}
.calendar-text-reader[open]{display:flex;flex-direction:column}.calendar-text-reader header{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px max(10px,env(safe-area-inset-right)) 8px max(10px,env(safe-area-inset-left));flex-shrink:0;border-bottom:1px solid var(--line)}
.calendar-text-reader h2{margin:0;font-size:18px;overflow-wrap:anywhere}.calendar-text-reader header button{min-height:44px;flex-shrink:0}
.calendar-text-body{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:8px max(10px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(10px,env(safe-area-inset-left));font:19px/1.65 Georgia,serif}
.calendar-text-body .slavonic-unicode{font-family:Ponomar,serif}.calendar-text-body .slavonic-civil{font-family:'Monomakh Unicode',serif;font-synthesis:none}
.calendar-text-body h3{font:600 15px/1.4 sans-serif}.calendar-verse{margin:0 0 8px;white-space:pre-wrap}.calendar-verse small{font:12px sans-serif}.reader-night{background:#101821;color:#e2eaf4}
</style>
