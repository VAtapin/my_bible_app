<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import type {BibleChapter,BibleVerse} from '@/api/contracts'
import {createVerseStudyApi} from '@/api/verseStudy'
import {apiBaseUrl} from '@/config/api'
import {verseStudyService,type ReferenceGroup} from '@/services/verseStudy'
import {backgroundReferences} from '@/services/backgroundReferences'
import {displayReferenceGroups,referencePreviewText} from '@/services/referenceDisplay'
import {readReferenceText} from '@/services/referenceReading'
import {useReferencePreferences} from '@/profile/referencePreferences'
import {createChapterService} from '@/services/chapterService'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {bibleApi} from '@/api'
import TemporaryPassage from './TemporaryPassage.vue'
const props=defineProps<{chapter:BibleChapter;verse:BibleVerse}>()
const {settings,initialize}=useReferencePreferences();initialize()
const service=verseStudyService(createVerseStudyApi({baseUrl:apiBaseUrl})),chapterService=createChapterService(bibleApi,createIndexedDbChapterRepository())
const root=ref<HTMLElement>(),visible=ref(false),groups=ref<ReferenceGroup[]>([]),temporary=ref<ReferenceGroup>()
let observer:IntersectionObserver|undefined
onMounted(()=>{if(!root.value)return;if(typeof IntersectionObserver==='undefined'){visible.value=true;return}observer=new IntersectionObserver(entries=>{visible.value=entries.some(entry=>entry.isIntersecting)},{root:root.value.closest('.continuous-scroll,.interleaved'),rootMargin:'40px'});observer.observe(root.value)})
onUnmounted(()=>observer?.disconnect())
let sourceKey=''
watch([visible,()=>props.verse.id,()=>props.verse.osis_ref,()=>props.chapter.translation.code,()=>settings.value.inlineSources],([shown],_,cleanup)=>{
 const key=JSON.stringify([props.chapter.translation.code,props.verse.id,props.verse.osis_ref,settings.value.inlineSources])
 if(key!==sourceKey){sourceKey=key;groups.value=[];temporary.value=undefined}
 if(!shown||groups.value.length)return
 const {id,osis_ref}=props.verse,code=props.chapter.translation.code
 cleanup(backgroundReferences.subscribe(async()=>{
  const value=await service.references(id,code,osis_ref)
  if(value.verse.osis_ref!==osis_ref)throw Error('Wrong reference source')
  const candidates=displayReferenceGroups(value.references,settings.value.inlineSources)
  if(!candidates.length)return []
  const actual=await readReferenceText({...candidates[0]!,targets:candidates.flatMap(group=>group.targets)},code,chapterService)
  return candidates.filter(group=>group.targets.every(target=>Boolean(actual[target.osis_ref]||referencePreviewText(target,''))))
 },value=>{groups.value=value}))
},{immediate:true})
</script>
<template><div ref="root" class="inline-references" data-no-reader-gesture><div v-if="groups.length" class="reference-list"><button v-for="group in groups" :key="group.key" @click="temporary=group">{{group.label}}</button></div><TemporaryPassage v-if="temporary" :group="temporary" :code="chapter.translation.code" :service="chapterService" @close="temporary=undefined"/></div></template>
<style scoped>.inline-references{min-height:1px;font:12px/1.35 sans-serif;clear:both}.reference-list{display:flex;gap:2px 8px;flex-wrap:wrap;margin:2px 0}.reference-list button{font:inherit;color:var(--brand-blue);padding:2px 0;border:0;background:none;min-height:0;margin:0;text-align:left}</style>