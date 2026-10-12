<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import type {BibleChapter,BibleVerse} from '@/api/contracts'
import type {CommentaryEntry} from '@/api/study'
import {commentarySources,inlineCommentaries} from '@/services/inlineCommentaries'
import {boundaryCommentaries,finalBoundaryFailure,type CommentaryBoundary} from '@/services/commentaryBoundaries'
import {backgroundReferences} from '@/services/backgroundReferences'
import {commentaryBoundaryMessages} from '@/i18n/commentaryBoundaries'
import {studyMessages} from '@/i18n/study'
import {useI18n} from '@/i18n'
import PrayerContent from '@/components/PrayerContent.vue'
import CommentaryAnnotations from './CommentaryAnnotations.vue'
import {prayerExcerpt} from '@/services/prayerContent'
const props=defineProps<{chapter:BibleChapter;edge:'start'|'end';anchor?:BibleVerse;boundary:CommentaryBoundary}>()
const {language}=useI18n(),labels=computed(()=>commentaryBoundaryMessages[language.value]),text=computed(()=>studyMessages[language.value])
const root=ref<HTMLElement>(),visible=ref(false),revision=ref(0),entries=ref<CommentaryEntry[]>([])
const expanded=ref(new Set<number>())
function toggle(id:number,event:Event){const next=new Set(expanded.value);if((event.currentTarget as HTMLDetailsElement).open)next.add(id);else next.delete(id);expanded.value=next}
const groups=computed(()=>{
 const result=new Map<string,{book:boolean;placement:'before'|'after'|null;entries:CommentaryEntry[]}>()
 for(const entry of entries.value){const book=entry.chapter_from===0,placement=entry.placement??null,key=`${book}:${placement}`;let group=result.get(key);if(!group){group={book,placement,entries:[]};result.set(key,group)}group.entries.push(entry)}
 return[...result.values()]
})
let observer:IntersectionObserver|undefined
function changed(){revision.value++}
onMounted(()=>{
 window.addEventListener('commentary-sources-changed',changed)
 if(!root.value)return
 if(typeof IntersectionObserver==='undefined'){visible.value=true;return}
 observer=new IntersectionObserver(values=>{if(values.some(value=>value.isIntersecting)){visible.value=true;observer?.disconnect()}},{root:root.value.closest('.continuous-scroll,.interleaved'),rootMargin:'120px'})
 observer.observe(root.value)
})
onUnmounted(()=>{observer?.disconnect();window.removeEventListener('commentary-sources-changed',changed)})
watch([visible,()=>props.chapter,()=>props.anchor?.osis_ref,()=>props.boundary.book,()=>props.boundary.chapterBoundary,revision],(_,__,cleanup)=>{
 entries.value=[]
 expanded.value=new Set()
 const verse=props.anchor??(props.edge==='start'?props.chapter.verses[0]:props.chapter.verses.at(-1))
 if(!visible.value||!verse)return
 const sources=commentarySources(verse.osis_ref)
 if(!sources.length)return
 cleanup(backgroundReferences.subscribe(async()=>{try{return boundaryCommentaries(
  await inlineCommentaries(verse.osis_ref,undefined,sources,props.chapter.translation.code,true),
  Number(verse.osis_ref.split('.')[1]),props.edge==='end'&&props.boundary.book,props.edge,props.edge==='start'&&props.boundary.book,props.boundary.chapterBoundary)}catch(error){if(finalBoundaryFailure(error))return [];throw error}},value=>{entries.value=value}))
},{immediate:true})
</script>
<template><div ref="root" class="boundary-materials" data-no-reader-gesture>
 <details v-for="group in groups" :key="`${group.book}:${group.placement}`">
  <summary>{{group.book?labels.book:labels.chapter}}<template v-if="group.placement"> · {{group.placement==='after'?labels.after:labels.before}}</template> · {{group.entries.length}}</summary>
  <details v-for="entry in group.entries" :key="entry.id" class="material-article" @toggle="toggle(entry.id,$event)">
   <summary><strong>{{prayerExcerpt(entry.title??'')||text.section}}</strong><small>{{prayerExcerpt(entry.author??'')}}<template v-if="entry.author"> · </template>{{prayerExcerpt(entry.module_name)}}</small></summary>
  <template v-if="expanded.has(entry.id)"><PrayerContent :content="entry.body"/><CommentaryAnnotations :annotations="entry.annotations" :source="entry.module_name" :module-code="entry.module_code" :translation-code="chapter.translation.code"/></template>
  </details>
 </details>
</div></template>
<style scoped>
.boundary-materials{clear:both;min-height:1px;font-size:13px;line-height:1.45}.boundary-materials details{margin:6px 0;border-block:1px solid var(--line);padding:6px 2px}.boundary-materials summary{cursor:pointer}.boundary-materials small{display:block;font:12px/1.5 sans-serif;color:var(--brand-blue)}.boundary-materials .material-article{border:0;margin:3px 0;padding:4px 0}.boundary-materials :deep(.prayer-content){font-size:var(--reading-size,19px)}
</style>
