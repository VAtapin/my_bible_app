<script setup lang="ts">
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import SourceVerseText from './SourceVerseText.vue'
import{createPersonalStudyRepository}from'@/offline/personalStudy'
import{emptyPersonalStudy,type SavedPassage,comparePoints}from'@/services/personalStudy'
import{inlineSourceStrongTokens}from'@/services/sourceVerseDisplay'
import{sourceStrongNumbers}from'@/services/strongMarkup'
import type {BibleChapter,BibleVerse} from '@/api/contracts'
import type {ChapterService} from '@/services/chapterService'
import {comparisonFrameRows,type ComparisonFrame} from '@/services/bibleComparison'
import {loadPairedContinuation} from '@/services/continuousReading'
import {bibleApi}from'@/api'
import InlineVerseCommentaries from '@/features/study/InlineVerseCommentaries.vue'
import InlineVerseReferences from '@/features/study/InlineVerseReferences.vue'
import {useI18n} from '@/i18n'
import {continuousReadingMessages} from '@/i18n/continuousReading'
import {useReaderPreferences,effectiveReaderPreferences} from '@/profile/readerPreferences'
import {attachReaderGestures,pageReader} from '@/services/readerGestures'
const props=defineProps<{primary:BibleChapter;secondary:BibleChapter;secondaryChapters?:BibleChapter[];selection?:SavedPassage;selectedVerse?:number;initialVerse?:number;service?:ChapterService;initialOffset?:number}>()
const emit=defineEmits<{strong:[number:string,source:BibleChapter,verse:BibleVerse];study:[source:BibleChapter,verse:BibleVerse];select:[number:number,chapter:BibleChapter];visible:[chapter:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number];chapter:[number];book:[number];actions:[source:BibleChapter,verse:BibleVerse,event?:MouseEvent]}>()
const {language,messages:text}=useI18n(),labels=computed(()=>continuousReadingMessages[language.value])
const {preferences,initialize}=useReaderPreferences();initialize()
const personal=ref(emptyPersonalStudy()),repository=createPersonalStudyRepository()
async function refreshPersonal(){try{personal.value=await repository.read()}catch{/* Reading is available while the personal editor reports storage errors. */}}
onMounted(()=>{void refreshPersonal();window.addEventListener('personal-study-changed',refreshPersonal)})
onUnmounted(()=>window.removeEventListener('personal-study-changed',refreshPersonal))
function inPassage(source:BibleChapter|undefined,verse:BibleVerse|undefined){const p=props.selection;return !!(source&&verse&&p&&p.translationCode===source.translation.code&&p.bookSlug===source.book.slug&&comparePoints(p.start,{chapter:source.chapter.number,verse:verse.number})<=0&&comparePoints(p.end,{chapter:source.chapter.number,verse:verse.number})>=0)}
const display=computed(()=>effectiveReaderPreferences(preferences.value))
type Frame=ComparisonFrame
const frames=ref<Frame[]>([{primary:props.primary,secondary:props.secondary,secondaryChapters:props.secondaryChapters}]),root=ref<HTMLElement>(),loading=ref(false),failed=ref<number>(),selected=ref('')
const rows=computed(()=>comparisonFrameRows(frames.value))
let stopped=false,timer:ReturnType<typeof setTimeout>|undefined,dispose:(()=>void)|undefined
async function load(direction:number){
 if(!props.service||loading.value||stopped)return
 const number=(direction<0?frames.value[0]!.primary.chapter.number:frames.value.at(-1)!.primary.chapter.number)+direction
 if(number<1||number>props.primary.book.chapters_count)return
 loading.value=true;failed.value=undefined
 try{
  const {primary,secondary,secondaryChapters}=await loadPairedContinuation(props.primary,props.secondary,number,props.service,bibleApi)
  if(stopped)return
  const viewport=root.value,area=viewport?.getBoundingClientRect(),anchor=viewport&&area?[...viewport.querySelectorAll<HTMLElement>('[data-pair-ref]')].find(el=>el.getBoundingClientRect().bottom>area.top):undefined,anchorRef=anchor?.dataset.pairRef,anchorOffset=anchor&&area?area.top-anchor.getBoundingClientRect().top:0
  const height=viewport?.scrollHeight??0,top=viewport?.scrollTop??0
  frames.value=direction<0?[{primary,secondary,secondaryChapters},...frames.value]:[...frames.value,{primary,secondary,secondaryChapters}]
  await nextTick();if(direction<0&&root.value){const restored=anchorRef?[...root.value.querySelectorAll<HTMLElement>('[data-pair-ref]')].find(el=>el.dataset.pairRef===anchorRef):undefined;if(restored)root.value.scrollTop+=restored.getBoundingClientRect().top-root.value.getBoundingClientRect().top+anchorOffset;else root.value.scrollTop=top+root.value.scrollHeight-height}
 }catch{if(!stopped)failed.value=direction}finally{loading.value=false;if(!failed.value)probe()}
}
function probe(){clearTimeout(timer);timer=setTimeout(()=>{
 const viewport=root.value;if(!viewport||stopped)return
 const area=viewport.getBoundingClientRect(),elements=[...viewport.querySelectorAll<HTMLElement>('[data-pair-ref]')].filter(el=>{const rect=el.getBoundingClientRect();return rect.bottom>area.top&&rect.top<area.bottom})
 const visible=elements.map(el=>({element:el,row:rows.value.find(row=>row.reference===el.dataset.pairRef)})).filter(value=>value.row?.primary)
 const first=visible[0],last=visible.filter(value=>value.row?.frame.primary.chapter.number===first?.row?.frame.primary.chapter.number).at(-1)
 if(first?.row?.primary&&last?.row?.primary)emit('visible',first.row.frame.primary,first.row.primary,last.row.primary,Math.max(0,area.top-first.element.getBoundingClientRect().top))
 if(viewport.scrollHeight-viewport.scrollTop-viewport.clientHeight<viewport.clientHeight*.65)void load(1)
 else if(viewport.scrollTop<viewport.clientHeight*.3)void load(-1)
},120)}
function font(chapter:BibleChapter){return chapter.translation.language.code==='cu'?'Ponomar, serif':chapter.translation.language.code==='cu-civil'?'"Monomakh Unicode", serif':'Georgia, serif'}
onMounted(async()=>{await nextTick();const number=props.initialVerse??props.selectedVerse,row=root.value?.querySelector<HTMLElement>(`[data-pair-ref="${props.primary.verses.find(v=>v.number===number)?.osis_ref}"]`);if(row&&root.value)root.value.scrollTop+=row.getBoundingClientRect().top-root.value.getBoundingClientRect().top+(props.initialOffset??0)})
onMounted(async()=>{await nextTick();dispose=root.value?attachReaderGestures(root.value,()=>display.value,{page:d=>pageReader(root.value!,d),chapter:d=>emit('chapter',d),book:d=>emit('book',d)}):undefined;probe()})
onUnmounted(()=>{stopped=true;clearTimeout(timer);dispose?.()})
</script>
<template><div ref="root" class="interleaved" :class="{'reader-night':display.night}" :style="{'--reading-size':`${display.fontSize}px`,'--reader-line-height':String(display.lineHeight)}" @scroll="probe">
 <p v-if="failed===-1" role="alert">{{labels.unavailable}} <button @click="load(-1)">{{labels.retry}}</button></p>
 <div v-for="row in rows" :key="row.reference" class="parallel-row" :data-pair-ref="row.reference" :class="{'study-range':inPassage(row.frame.primary,row.primary)||inPassage(row.secondaryChapter,row.secondary),'selected-verse':selected?row.reference===selected:row.frame.primary.chapter.number===primary.chapter.number&&row.primary?.number===selectedVerse}">
  <div v-for="(value,index) in [row.primary,row.secondary]" :key="index" class="parallel-verse" :style="{fontFamily:font(index===0?row.frame.primary:row.secondaryChapter??secondary)}">
   <small v-if="!display.clean">{{(index===0?row.frame.primary:row.secondaryChapter??secondary).translation.name}} · {{row.reference}}</small>
   <button v-if="value" class="verse-text" @click="selected=row.reference;emit('select',value.number,index===0?row.frame.primary:row.secondaryChapter!)" @contextmenu.prevent="emit('actions',index===0?row.frame.primary:row.secondaryChapter!,value,$event)"><span v-if="display.verseNumbers" class="verse-number">{{value.number}}</span><SourceVerseText :verse="value" :code="(index===0?row.frame.primary:row.secondaryChapter!).translation.code" :marks="personal.marks" :preferences="display" :palette="personal.palette" @strong="number=>emit('strong',number,index===0?row.frame.primary:row.secondaryChapter!,value)"/></button>
   <span v-if="value&&display.strongNumbers" class="source-strong"><button v-for="number in sourceStrongNumbers(value.text,value.has_strong_markup).filter(number=>!inlineSourceStrongTokens(value).some(token=>token.strong_number===number))" :key="number" @click="emit('strong',number,index===0?row.frame.primary:row.secondaryChapter!,value)">{{number}}</button></span>
   <p v-if="!value">{{text.parallel.missing}}</p>
   <InlineVerseCommentaries v-if="value&&display.commentaryLinks" :chapter="index===0?row.frame.primary:row.secondaryChapter!" :verse="value" @study="(source,item)=>emit('study',source,item)"/>
   <InlineVerseReferences v-if="value&&display.crossReferences" :chapter="index===0?row.frame.primary:row.secondaryChapter!" :verse="value" @study="(source,item)=>emit('study',source,item)"/>
  </div>
 </div><p v-if="loading" role="status">{{text.loading}}</p><p v-if="failed===1" role="alert">{{labels.unavailable}} <button @click="load(1)">{{labels.retry}}</button></p>
</div></template>
<style scoped>.interleaved{height:65dvh;overflow:auto;overflow-anchor:none;overscroll-behavior:contain}.parallel-row.study-range{border-left:4px solid #3883c0}.source-strong{display:flex;flex-wrap:wrap;gap:4px}.source-strong button{font-size:12px;min-height:32px}.parallel-row{border-bottom:1px solid var(--line)}.parallel-verse{padding:10px 12px;font-size:var(--reading-size,19px);line-height:var(--reader-line-height,1.55);overflow-wrap:anywhere}.parallel-verse p{margin:0}.parallel-verse small{display:block;font:12px sans-serif;margin-bottom:5px}.reader-night{background:#101821;color:#e2eaf4}.reader-night .verse-text{color:#e2eaf4}</style>
