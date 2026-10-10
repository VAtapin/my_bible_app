<script setup lang="ts">
import{referencePreviewText}from'@/services/referenceDisplay'
import ReferenceNumbering from './ReferenceNumbering.vue'
import{computed,nextTick,onMounted,onUnmounted,ref,watch}from'vue'
import type{BibleChapter,BibleVerse}from'@/api/contracts'
import{createVerseStudyApi,type StudyReferences}from'@/api/verseStudy'
import{apiBaseUrl}from'@/config/api'
import{verseStudyService}from'@/services/verseStudy'
import{displayReferenceGroups,referenceCopyText}from'@/services/referenceDisplay'
import{useReferencePreferences,toggleReferenceSource}from'@/profile/referencePreferences'
import{useI18n}from'@/i18n'
import{verseStudyMessages}from'@/i18n/verseStudy'
import{inlineReferenceMessages}from'@/i18n/inlineReferences'
import{createChapterService}from'@/services/chapterService'
import{createIndexedDbChapterRepository}from'@/offline/indexedDbChapterRepository'
import{bibleApi}from'@/api'
import TemporaryPassage from './TemporaryPassage.vue'
import type{ReferenceGroup}from'@/services/verseStudy'
const props=defineProps<{chapter:BibleChapter;verse:BibleVerse}>()
const emit=defineEmits<{study:[source:BibleChapter,verse:BibleVerse]}>()
const{language,messages}=useI18n(),labels=computed(()=>verseStudyMessages[language.value]),text=computed(()=>inlineReferenceMessages[language.value])
const{settings,initialize,setSettings}=useReferencePreferences();initialize()
const service=verseStudyService(createVerseStudyApi({baseUrl:apiBaseUrl})),chapterService=createChapterService(bibleApi,createIndexedDbChapterRepository())
const root=ref<HTMLElement>(),modal=ref<HTMLDialogElement>(),visible=ref(false),data=ref<StudyReferences>(),error=ref(false),retry=ref(0),copied=ref(false),temporary=ref<ReferenceGroup>()
const groups=computed(()=>displayReferenceGroups(data.value?.references??[],settings.value.inlineSources)),sources=computed(()=>[...new Set(data.value?.references.map(item=>item.source??'')??[])])
let observer:IntersectionObserver|undefined
onMounted(()=>{if(!root.value)return;if(typeof IntersectionObserver==='undefined'){visible.value=true;return}observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){visible.value=true;observer?.disconnect()}},{root:root.value.closest('.continuous-scroll,.interleaved'),rootMargin:'40px'});observer.observe(root.value)})
onUnmounted(()=>observer?.disconnect())
watch([visible,()=>props.verse.id,()=>props.chapter.translation.code,retry],async([shown],_,cleanup)=>{let stale=false;cleanup(()=>stale=true);if(!shown)return;data.value=undefined;error.value=false;try{const value=await service.references(props.verse.id,props.chapter.translation.code);if(value.verse.osis_ref!==props.verse.osis_ref)throw Error('Wrong reference source');if(!stale)data.value=value}catch{if(!stale)error.value=true}},{immediate:true})
async function open(){await nextTick();modal.value?.showModal()}
async function copy(group:ReferenceGroup){try{await navigator.clipboard.writeText(referenceCopyText(group,props.chapter.translation.name));copied.value=true}catch{error.value=true}}
</script>
<template><div ref="root" class="inline-references" data-no-reader-gesture>
 <button v-if="error" @click="retry++">↗ {{labels.retry}}</button><small v-else-if="visible&&!data">{{messages.loading}}</small>
 <template v-else-if="data"><button @click="open" :aria-label="`${labels.references}: ${groups.length}`">↗ {{groups.length}}</button><div v-if="settings.inlineMode==='list'" class="reference-list"><button v-for="group in groups" :key="group.key" @click="temporary=group">{{group.label}}</button></div></template>
 <dialog ref="modal" class="reference-popup"><header><strong>{{chapter.book.name}} {{chapter.chapter.number}}:{{verse.number}} · {{chapter.translation.name}}</strong><button @click="modal?.close()">{{labels.close}}</button></header><p>{{text.count}}: {{groups.length}}</p>
 <details><summary>{{text.normal}}</summary><button @click="setSettings({...settings,inlineSources:null})">{{labels.all}}</button><label v-for="source in sources" :key="source"><input type="checkbox" :checked="settings.inlineSources===null||settings.inlineSources.includes(source)" @change="setSettings({...settings,inlineSources:toggleReferenceSource(settings.inlineSources,source,sources)})"/>{{source||'—'}}</label></details>
 <article v-for="group in groups" :key="group.key"><strong>{{group.label}}</strong><ReferenceNumbering :targets="group.targets"/><small> · {{group.source||'—'}} · {{chapter.translation.name}}</small><p v-for="target in group.targets" :key="target.osis_ref">{{target.verse_number}} {{referencePreviewText(target,labels.missing)}}</p><button @click="temporary=group">{{labels.open}}</button><button @click="copy(group)">{{text.copy}}</button></article><p v-if="copied">{{text.copied}}</p><button @click="modal?.close();emit('study',chapter,verse)">{{labels.title}}</button>
 </dialog><TemporaryPassage v-if="temporary" :group="temporary" :code="chapter.translation.code" :service="chapterService" @close="temporary=undefined"/>
</div></template>
<style scoped>.inline-references{font:12px sans-serif;padding:2px 0;clear:both}.inline-references button{font:inherit;min-height:30px}.reference-list{display:flex;gap:4px;flex-wrap:wrap}.reference-popup{max-width:min(640px,calc(100vw - 28px));max-height:85dvh;overflow:auto;border:1px solid var(--line);border-radius:12px;background:var(--white);color:var(--ink)}.reference-popup::backdrop{background:#0006}.reference-popup header{display:flex;justify-content:space-between;gap:10px}.reference-popup label{display:block;padding:5px}.reference-popup article{border-bottom:1px solid var(--line);padding:10px 0}.reference-popup p{white-space:pre-wrap}.reference-popup button{margin-right:6px}</style>
