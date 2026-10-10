<script setup lang="ts">
import{referenceSourceLabel}from'@/services/referenceDisplay'
import ReferencePassageCard from './ReferencePassageCard.vue'
import{computed,nextTick,ref,watch}from'vue'
import{RouterLink}from'vue-router'
import type{BibleChapter,BibleBook}from'@/api/contracts'
import{createVerseStudyApi,type StudyStrongEntry,type StudyReferences}from'@/api/verseStudy'
import{apiBaseUrl}from'@/config/api'
import{verseStudyService,type ReferenceGroup}from'@/services/verseStudy'
import{webBibleBooks}from'@/services/webBibleLibrary'
import{bibleApi}from'@/api'
import{useI18n}from'@/i18n'
import{verseStudyMessages}from'@/i18n/verseStudy'
import{prayerBlocks}from'@/services/prayerContent'
import{installedStrongEntries}from'@/services/studyPackageLookup'
import SourceCard from'./SourceCard.vue'
import{displayReferenceGroups,referenceCopyText}from'@/services/referenceDisplay'
import{useReferencePreferences,toggleReferenceSource}from'@/profile/referencePreferences'
import{inlineReferenceMessages}from'@/i18n/inlineReferences'
const props=defineProps<{chapter:BibleChapter;verse?:number}>()
const emit=defineEmits<{open:[group:ReferenceGroup]}>()
const{language,messages}=useI18n(),labels=computed(()=>verseStudyMessages[language.value])
const service=verseStudyService(createVerseStudyApi({baseUrl:apiBaseUrl}))
const verse=computed(()=>props.chapter.verses.find(v=>v.number===props.verse)??props.chapter.verses[0])
const references=ref<StudyReferences>(),books=ref<BibleBook[]>([]),referenceError=ref(false),retry=ref(0)
const selectedSource=ref(''),sort=ref('canonical'),selectedNumber=ref(''),entry=ref<StudyStrongEntry>(),entryError=ref(false),entryRetry=ref(0)
const strongSource=ref(''),strongSources=ref<Record<string,unknown>[]>([])
const referencePrefs=useReferencePreferences();referencePrefs.initialize()
const referenceLabels=computed(()=>inlineReferenceMessages[language.value]),copied=ref(false)
const groups=computed(()=>{const order=Object.fromEntries(books.value.filter(b=>b.canonical_book).map(b=>[b.canonical_book!.osis_code,b.order]));const list=displayReferenceGroups(references.value?.references??[],referencePrefs.settings.value.detailSources,order);return referencePrefs.settings.value.detailSort==='source'?list.sort((a,b)=>a.source.localeCompare(b.source)):list})
async function copyReference(group:ReferenceGroup,actual:Record<string,string>={}){try{await navigator.clipboard.writeText(referenceCopyText(group,props.chapter.translation.name,actual));copied.value=true}catch{referenceError.value=true}}
const sources=computed(()=>[...new Set(references.value?.references.map(r=>r.source??'')??[])].filter(source=>referenceSourceLabel(source)))
watch([verse,()=>props.chapter.translation.code,retry],async([item],_,cleanup)=>{
  let stale=false;cleanup(()=>stale=true);references.value=undefined;referenceError.value=false;selectedNumber.value='';if(!item)return
  void webBibleBooks(bibleApi,props.chapter.translation.code).then(value=>{if(!stale)books.value=value}).catch(()=>{})
  await Promise.allSettled([
    service.references(item.id,props.chapter.translation.code,item.osis_ref).then(value=>{if(value.verse.osis_ref!==item.osis_ref)throw new Error('Wrong verse');if(!stale)references.value=value}).catch(()=>{if(!stale)referenceError.value=true}),
  ])
},{immediate:true})
watch(selectedNumber,async(number,_,cleanup)=>{let stale=false;cleanup(()=>stale=true);strongSource.value='';strongSources.value=[];if(!number)return;try{const data=await installedStrongEntries(number);if(!stale&&data)strongSources.value=data.lexicons.filter(l=>data.entries.some(e=>e.lexicon_code===l.code))}catch{}})
watch([selectedNumber,entryRetry,strongSource],async([number],_,cleanup)=>{let stale=false;cleanup(()=>stale=true);entry.value=undefined;entryError.value=false;if(!number)return;try{const value=await service.strong(number,verse.value?.id,strongSource.value||undefined);if(!stale)entry.value=value}catch{if(!stale)entryError.value=true}})
const strongHeading=ref<HTMLElement>()
defineExpose({openStrong:async(number:string)=>{selectedNumber.value=number;await nextTick();const heading=strongHeading.value,aside=heading?.closest('aside');if(heading&&aside)aside.scrollTop+=heading.getBoundingClientRect().top-aside.getBoundingClientRect().top-16}})
</script>
<template><section class="verse-study-panel">
  <h3>{{labels.title}} · {{chapter.book.name}} {{chapter.chapter.number}}:{{verse?.number}}</h3>
  <h4 v-if="selectedNumber" ref="strongHeading">{{labels.strong}}</h4>
  <template v-if="selectedNumber"><label v-if="strongSources.length">{{ labels.source }}<select v-model="strongSource"><option value="">{{ labels.all }}</option><option v-for="source in strongSources" :key="String(source.code)" :value="String(source.code)">{{ source.name }} · {{ source.language }}</option></select></label><p v-if="entryError" role="alert">{{labels.error}} <button @click="entryRetry++">{{labels.retry}}</button></p><p v-else-if="!entry" role="status">{{messages.loading}}</p><article v-else class="strong-entry"><h4>{{selectedNumber}} · {{entry.word}}</h4><p v-if="entry.transliteration">{{labels.transliteration}}: {{entry.transliteration}}</p><p v-if="entry.pronunciation">{{labels.pronunciation}}: {{entry.pronunciation}}</p><small>{{entry.lexicon.name}} · {{entry.lexicon.language}}</small><SourceCard :metadata="strongSources.find(source=>source.code===entry?.lexicon.code)??entry.lexicon"/><p v-for="(block,index) in prayerBlocks(entry.content??'')" :key="index"><template v-for="(segment,n) in block.segments" :key="n"><strong v-if="segment.strong">{{segment.text}}</strong><em v-else-if="segment.emphasis">{{segment.text}}</em><template v-else>{{segment.text}}</template></template></p><RouterLink :to="{path:'/search',query:{q:selectedNumber,translation:chapter.translation.code,match:'strong'}}">{{labels.occurrences}}</RouterLink></article></template>
  <template v-if="groups.length||!references||referenceError"><h4>{{labels.references}}</h4>
  <div class="reference-controls"><details v-if="sources.length>1"><summary>{{referenceLabels.detail}}</summary><button @click="referencePrefs.setSettings({...referencePrefs.settings.value,detailSources:null})">{{labels.all}}</button><label v-for="source in sources" :key="source"><input type="checkbox" :checked="referencePrefs.settings.value.detailSources===null||referencePrefs.settings.value.detailSources.includes(source)" @change="referencePrefs.setSettings({...referencePrefs.settings.value,detailSources:toggleReferenceSource(referencePrefs.settings.value.detailSources,source,sources)})"/>{{referenceSourceLabel(source)}}</label></details><label>{{labels.sort}}<select :value="referencePrefs.settings.value.detailSort" @change="referencePrefs.setSettings({...referencePrefs.settings.value,detailSort:($event.target as HTMLSelectElement).value==='source'?'source':'canonical'})"><option value="canonical">{{labels.canonical}}</option><option value="source">{{labels.bySource}}</option></select></label></div>
  <p v-if="references">{{referenceLabels.count}}: {{groups.length}}</p><p v-if="copied">{{referenceLabels.copied}}</p>
  <p v-if="referenceError" role="alert">{{labels.error}} <button @click="retry++">{{labels.retry}}</button></p><p v-else-if="!references" role="status">{{messages.loading}}</p><p v-else-if="!groups.length">{{labels.none}}</p>
  <ReferencePassageCard v-for="group in groups" :key="group.key" :group="group" :code="chapter.translation.code" @open="emit('open',$event)" @copy="copyReference"/>
</template>
</section></template>
<style scoped>.verse-study-panel{padding:12px;margin-top:12px;border:1px solid var(--line);border-radius:12px}.reference-controls{display:flex;gap:8px;flex-wrap:wrap}.reference-controls label{flex:1;min-width:110px}.study-reference,.strong-entry{padding:12px 0;border-bottom:1px solid var(--line)}.study-reference p{white-space:pre-wrap;overflow-wrap:anywhere}</style>
