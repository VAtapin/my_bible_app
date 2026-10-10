<script setup lang="ts">
import { computed,nextTick,onMounted,onUnmounted,ref,watch } from 'vue'
import type { BibleChapter,BibleVerse } from '@/api/contracts'
import type { CommentaryEntry } from '@/api/study'
import { commentarySources,inlineCommentaries } from '@/services/inlineCommentaries'
import { studyMessages } from '@/i18n/study'
import { useI18n } from '@/i18n'
import PrayerContent from '@/components/PrayerContent.vue'
import CommentaryAnnotations from './CommentaryAnnotations.vue'
import { prayerExcerpt } from '@/services/prayerContent'
const props=defineProps<{chapter:BibleChapter;verse:BibleVerse}>(),emit=defineEmits<{study:[chapter:BibleChapter,verse:BibleVerse]}>()
const {language}=useI18n(),text=computed(()=>studyMessages[language.value]),root=ref<HTMLElement>(),dialog=ref<HTMLDialogElement>(),visible=ref(false),revision=ref(0),entries=ref<CommentaryEntry[]>([]),expanded=ref(new Set<number>()),failed=ref(false)
let observer:IntersectionObserver|undefined
function changed(){revision.value++}
onMounted(()=>{window.addEventListener('commentary-sources-changed',changed);if(!root.value)return;if(typeof IntersectionObserver==='undefined'){visible.value=true;return};observer=new IntersectionObserver(values=>{if(values.some(v=>v.isIntersecting)){visible.value=true;observer?.disconnect()}},{root:root.value.closest('.continuous-scroll,.interleaved'),rootMargin:'40px'});observer.observe(root.value)})
onUnmounted(()=>{observer?.disconnect();window.removeEventListener('commentary-sources-changed',changed)})
watch([visible,()=>props.verse.osis_ref,()=>props.chapter.translation.code,revision],async(_,__,cleanup)=>{let stale=false;cleanup(()=>stale=true);if(!visible.value)return;entries.value=[];failed.value=false;const sources=commentarySources(props.verse.osis_ref);if(!sources.length)return;try{const data=await inlineCommentaries(props.verse.osis_ref,undefined,sources,props.chapter.translation.code);if(!stale)entries.value=data}catch{if(!stale)failed.value=true}},{immediate:true})
async function open(){await nextTick();dialog.value?.showModal()}
</script>
<template><div ref="root" class="inline-commentaries" data-no-reader-gesture><button v-if="entries.length" @click="open">▤ {{text.commentaries}} · {{entries.length}}</button><button v-else-if="failed" @click="revision++">▤ {{text.retry}}</button><dialog ref="dialog"><header><strong>{{chapter.book.name}} {{chapter.chapter.number}}:{{verse.number}}</strong><button :aria-label="text.close" @click="dialog?.close()">×</button></header><article v-for="entry in entries" :key="entry.id"><strong>{{prayerExcerpt(entry.title??text.section)}}</strong><small> · {{prayerExcerpt(entry.module_name)}}</small><PrayerContent v-if="expanded.has(entry.id)" :content="entry.body"/><CommentaryAnnotations v-if="expanded.has(entry.id)" :annotations="entry.annotations" :source="entry.module_name" :module-code="entry.module_code" :translation-code="chapter.translation.code"/><p v-else>{{prayerExcerpt(entry.body)}}</p><button v-if="!expanded.has(entry.id)" @click="expanded=new Set([...expanded,entry.id])">{{text.full}}</button></article><button @click="dialog?.close();emit('study',chapter,verse)">{{text.commentaries}}</button></dialog></div></template>
<style scoped>.inline-commentaries{min-height:1px;font:12px sans-serif;clear:both}.inline-commentaries button{font:inherit;min-height:30px}dialog{max-width:min(640px,calc(100vw - 28px));max-height:85dvh;overflow:auto;border:1px solid var(--line);border-radius:12px;background:var(--white);color:var(--ink)}dialog::backdrop{background:#0006}header{display:flex;justify-content:space-between}article{padding:12px 0;border-bottom:1px solid var(--line)}</style>
