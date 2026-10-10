<script setup lang="ts">
import{computed,onMounted,onUnmounted,ref,watch,reactive}from'vue'
import{snapshotWindows,type WindowSnapshot}from'@/services/temporaryWindow'
import{temporaryWindowMessages}from'@/i18n/temporaryWindow'
import type{BibleChapter,BibleVerse,TranslationSummary}from'@/api/contracts'
import{bibleApi}from'@/api'
import type{SavedPassage}from'@/services/personalStudy'
import type{ChapterService}from'@/services/chapterService'
import{resolvedVerseChapter}from'@/services/verseLocations'
import{loadComparison,loadComparisonChapters}from'@/services/bibleComparison'
import{readWebChapter}from'@/services/webBibleLibrary'
import{readWindows,windowRatio,mergeWindowLocation,type ReaderWindows,type WindowPlace}from'@/services/readerWindows'
import{readerWindowMessages}from'@/i18n/readerWindows'
import{useI18n}from'@/i18n'
import ReadingWindow from'./ReadingWindow.vue'
import ComparisonRows from'./ComparisonRows.vue'
import { ReaderNavigationHistory, type ReaderHistoryPlace } from '@/services/readerHistory'
import ReaderHistoryPanel from './ReaderHistoryPanel.vue'
const props=defineProps<{primary:BibleChapter;catalog:TranslationSummary[];service:ChapterService;selectedVerse?:number;primaryOffset?:number;bookmarks?:Set<string>;selection?:SavedPassage}>()
const emit=defineEmits<{study:[chapter:BibleChapter,verse:BibleVerse];strong:[number:string,chapter:BibleChapter,verse:BibleVerse];select:[number:number,source?:BibleChapter];visible:[chapter:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number];bookmark:[source:BibleChapter,verse:BibleVerse];actions:[source:BibleChapter,verse:BibleVerse,event?:MouseEvent]}>()
const{language,messages:text}=useI18n(),labels=computed(()=>readerWindowMessages[language.value])
const key='bible-desktop:reading-windows:v1',saved=readWindows(localStorage.getItem(key))
const point=(source:BibleChapter,verse=props.selectedVerse??1,offset=0):WindowPlace=>({code:source.translation.code,book:source.book.slug,chapter:source.chapter.number,verse,offset})
const initial=point(props.primary,props.selectedVerse??1,props.primaryOffset??0),code=props.catalog.find(item=>item.code!==initial.code)?.code??initial.code
const state=ref<ReaderWindows>(saved?mergeWindowLocation(saved,initial):{places:[initial,{...initial,code}],active:0,sync:true,ratio:.5,open:[true,true]})
const mode=ref(localStorage.getItem('bible-desktop:compare-mode')==='panes'?'panes':'interleaved')
const previewSources=ref<[BibleChapter?,BibleChapter?]>([]),previewSnapshot=ref<WindowSnapshot>(),temporaryLabels=computed(()=>temporaryWindowMessages[language.value])
const anchors=ref<[BibleChapter?,BibleChapter?]>([]),current=ref<[BibleChapter?,BibleChapter?]>([]),errors=ref<[string?,string?]>([]),follow=ref<[number?,number?]>([])
const paired=ref<BibleChapter[]>(),pairError=ref(false),root=ref<HTMLElement>(),windows=ref<InstanceType<typeof ReadingWindow>[]>([]),pairPicker=ref<InstanceType<typeof ReadingWindow>>()
const request=ref([0,0]),followRequests=ref([0,0]),epoch=ref(0),emptyBookmarks=new Set<string>()
let stopped=false,lastSync='',pairRequest=0
const activeSource=computed(()=>current.value[state.value.active]??anchors.value[state.value.active])
const histories=([0,1]as const).map(id=>reactive(new ReaderNavigationHistory(localStorage,`bible-desktop:reader-history:window-${id}`)))
const historyOpen=ref(false),activeHistory=computed(()=>histories[state.value.active]!)
const other=()=>state.value.active===0?1:0
const shape=computed(()=>state.value.open.every(Boolean)?`${state.value.ratio}fr 24px ${1-state.value.ratio}fr`:'1fr')
watch(state,value=>localStorage.setItem(key,JSON.stringify(previewSnapshot.value?.state??value)),{deep:true})
watch(mode,value=>localStorage.setItem('bible-desktop:compare-mode',previewSnapshot.value?.mode??value))
watch(mode,()=>{for(const id of [0,1]as const){const actual=current.value[id],place=state.value.places[id];if(actual&&actual.translation.code===place.code&&actual.book.slug===place.book&&actual.chapter.number===place.chapter){anchors.value[id]=actual;follow.value[id]=place.verse;followRequests.value[id]++}}})
async function loadPlace(id:0|1,place:WindowPlace) {
  const version=++request.value[id];errors.value[id]=undefined
  try {
    const value=place.code===props.primary.translation.code&&place.book===props.primary.book.slug&&place.chapter===props.primary.chapter.number?props.primary:await readWebChapter(props.service,place.code,place.book,place.chapter)
    if(stopped||version!==request.value[id])return
    if(value.translation.code!==place.code||value.book.slug!==place.book||value.chapter.number!==place.chapter||(place.verse>0&&!value.verses.some(v=>v.number===place.verse)))throw new Error('Wrong passage')
    state.value.places[id]={...place};anchors.value[id]=value;current.value[id]=value;follow.value[id]=place.verse
    followRequests.value[id]++
    if(!previewSnapshot.value&&id===state.value.active){emit('select',place.verse,value);const verse=value.verses.find(v=>v.number===place.verse);if(verse&&mode.value==='interleaved')emit('visible',value,verse,verse,place.offset)}
  }catch{if(!stopped&&version===request.value[id])errors.value[id]=labels.value.error}
}
async function synchronize(source:BibleChapter,first:BibleVerse) {
  if(!state.value.sync||!state.value.open.every(Boolean))return
  const id=other(),token=`${state.value.active}:${first.osis_ref}:${state.value.places[id].code}`
  if(token===lastSync)return
  lastSync=token
  const version=++request.value[id]
  try {
    const code=state.value.places[id].code
    const existing=anchors.value[id]
    const value=existing?.translation.code===code&&existing.chapter.number===source.chapter.number&&existing.verses.some(v=>v.osis_ref===first.osis_ref)?existing:code===source.translation.code?source:await resolvedVerseChapter(code,first.osis_ref,props.service)
    if(stopped||version!==request.value[id])return
    const target=value.verses.find(v=>v.osis_ref===first.osis_ref&&v.plain_text.trim())
    if(!target){errors.value[id]=labels.value.missing;return}
    errors.value[id]=undefined;state.value.places[id]=point(value,target.number,0);anchors.value[id]=value;current.value[id]=value;follow.value[id]=target.number
    followRequests.value[id]++
  }catch{if(!stopped&&version===request.value[id])errors.value[id]=labels.value.missing}
}
function visible(id:0|1,source:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number) {
  current.value[id]=source;state.value.places[id]=point(source,first.number,offset)
  if(!previewSnapshot.value)histories[id]!.observe(point(source,first.number,offset))
  if(!previewSnapshot.value&&id===state.value.active){emit('visible',source,first,last,offset);if(mode.value==='panes')void synchronize(source,first)}
}
async function pair(){
  const source=anchors.value[state.value.active];if(!source||mode.value!=='interleaved')return
  const version=++pairRequest;pairError.value=false;paired.value=undefined
  try {const code=state.value.places[other()].code;const value=code===source.translation.code?[source]:await loadComparisonChapters(source,code,bibleApi,props.service);if(!stopped&&version===pairRequest)paired.value=value}
  catch{if(!stopped&&version===pairRequest)pairError.value=true}
}
watch([mode,()=>anchors.value[state.value.active],()=>state.value.places[other()].code],()=>void pair())
async function moveBook(direction:number){const id=state.value.active;try{const list=await import('@/services/webBibleLibrary').then(module=>module.webBibleBooks(bibleApi,state.value.places[id].code)),target=list[list.findIndex(book=>book.slug===state.value.places[id].book)+direction];if(target)await navigate(id,{...state.value.places[id],book:target.slug,chapter:1,verse:1,offset:0})}catch{errors.value[id]=labels.value.error}}
async function navigate(id:0|1,place:WindowPlace,restoring=false){state.value.active=id;lastSync='';const before=request.value[id];await loadPlace(id,place);if(!previewSnapshot.value&&!restoring&&!errors.value[id]&&request.value[id]>before)histories[id]!.navigate(place);void pair()}
async function restoreHistory(place?:ReaderHistoryPlace){if(place)await navigate(state.value.active,place,true)}
async function move(offset:number){const id=state.value.active,place=state.value.places[id],source=activeSource.value;if(!source)return;const number=place.chapter+offset;if(number<1||number>source.book.chapters_count)return;await navigate(id,{...place,chapter:number,verse:1,offset:0})}
function activate(id:0|1){if(state.value.active===id)return;state.value.active=id;lastSync='';if(previewSnapshot.value)return;const source=current.value[id],verse=source?.verses.find(v=>v.number===state.value.places[id].verse);if(source&&verse){emit('select',verse.number,source);emit('visible',source,verse,verse,state.value.places[id].offset);if(mode.value==='panes')void synchronize(source,verse)}}
function swap(){epoch.value++;[state.value.places[0],state.value.places[1]]=[state.value.places[1],state.value.places[0]];[anchors.value[0],anchors.value[1]]=[anchors.value[1],anchors.value[0]];[current.value[0],current.value[1]]=[current.value[1],current.value[0]];[errors.value[0],errors.value[1]]=[errors.value[1],errors.value[0]];[follow.value[0],follow.value[1]]=[follow.value[1],follow.value[0]];state.value.active=state.value.open[other()]?other():state.value.active;lastSync='';void pair()}
async function changePair(code:string){const id=other(),source=activeSource.value;if(!source)return;try{const value=code===source.translation.code?source:await loadComparison(source,code,bibleApi,props.service);await loadPlace(id,point(value,state.value.places[state.value.active].verse));void pair()}catch{pairError.value=true}}
async function changeActiveTranslation(code:string){const id=state.value.active,source=activeSource.value;if(!source)return;try{const ref=source.verses.find(v=>v.number===state.value.places[id].verse)?.osis_ref;if(!ref)throw Error('Missing source');const value=code===source.translation.code?source:await resolvedVerseChapter(code,ref,props.service),verse=value.verses.find(v=>v.osis_ref===ref&&v.plain_text.trim());if(!verse)throw Error('Missing verse');await navigate(id,point(value,verse.number))}catch{errors.value[id]=labels.value.missing}}
async function toggleSync(){if(previewSnapshot.value)return;state.value.sync=!state.value.sync;lastSync='';const source=activeSource.value,verse=source?.verses.find(v=>v.number===state.value.places[state.value.active].verse);if(source&&verse)await synchronize(source,verse)}
function close(id:0|1){if(!state.value.open[other()]&&id===state.value.active)return;state.value.open[id]=false;if(id===state.value.active)activate(id===0?1:0)}
async function reopen(id:0|1){state.value.open[id]=true;await loadPlace(id,state.value.places[id]);lastSync='';const source=activeSource.value,verse=source?.verses.find(v=>v.number===state.value.places[state.value.active].verse);if(source&&verse)void synchronize(source,verse)}
function resize(event:PointerEvent){const element=root.value?.querySelector('.panes');if(!element)return;const bounds=element.getBoundingClientRect();state.value.ratio=windowRatio((event.clientY-bounds.top)/bounds.height)}
function drag(event:PointerEvent){(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);resize(event)}
onMounted(async()=>{if(!state.value.open[state.value.active])state.value.active=other();let missingDefault=false;if(!saved&&code!==initial.code){try{const value=await loadComparison(props.primary,code,bibleApi,props.service);state.value.places[1]=point(value)}catch{errors.value[1]=labels.value.missing;missingDefault=true}}await Promise.all(([0,1]as const).filter(id=>state.value.open[id]&&!(id===1&&missingDefault)).map(id=>loadPlace(id,state.value.places[id])));await pair()})
onUnmounted(()=>{stopped=true})
async function preview(source:BibleChapter,verse:number,id:0|1){
 if(!source.verses.some(v=>v.number===verse&&v.plain_text.trim()))throw Error('Exact verse absent');
 if(!previewSnapshot.value){previewSnapshot.value=snapshotWindows(state.value,mode.value);previewSources.value=[current.value[0],current.value[1]];}
 request.value[0]++;request.value[1]++;pairRequest++;state.value.sync=false;state.value.active=id;state.value.open[id]=true;mode.value='panes';lastSync='';epoch.value++;state.value.places[id]=point(source,verse);anchors.value[id]=source;current.value[id]=source;errors.value[id]=undefined;follow.value[id]=verse;followRequests.value[id]++;
}
async function returnFromPreview(){const saved=previewSnapshot.value;if(!saved)return;request.value[0]++;request.value[1]++;pairRequest++;epoch.value++;state.value=snapshotWindows(saved.state,saved.mode).state;state.value.sync=false;await Promise.all(([0,1]as const).filter(id=>state.value.open[id]).map(async id=>{const source=previewSources.value[id],place=saved.state.places[id];if(source&&source.translation.code===place.code&&source.book.slug===place.book&&source.chapter.number===place.chapter){anchors.value[id]=source;current.value[id]=source;follow.value[id]=place.verse;followRequests.value[id]++;errors.value[id]=undefined}else await loadPlace(id,place)}));mode.value=saved.mode;state.value.sync=saved.state.sync;const source=current.value[state.value.active],verse=source?.verses.find(v=>v.number===state.value.places[state.value.active].verse);lastSync=verse?`${state.value.active}:${verse.osis_ref}:${state.value.places[other()].code}`:'';previewSnapshot.value=undefined;if(source&&verse){emit('select',verse.number,source);emit('visible',source,verse,verse,state.value.places[state.value.active].offset)}void pair()}
defineExpose({source:()=>activeSource.value,preview,returnFromPreview,move,changeTranslation:changeActiveTranslation,historyBack:()=>{if(!previewSnapshot.value)return restoreHistory(activeHistory.value.back())},historyForward:()=>{if(!previewSnapshot.value)return restoreHistory(activeHistory.value.forward())},showHistory:()=>{if(!previewSnapshot.value)historyOpen.value=true},choosePlace:()=>{const id=state.value.active;if(mode.value==='interleaved')pairPicker.value?.openPicker();else windows.value.find(window=>window.$el.dataset.window===String(id))?.openPicker()}})
</script>
<template><section ref="root" class="parallel-reading">
  <ReaderHistoryPanel v-if="historyOpen" :state="activeHistory.state" :can-back="activeHistory.canBack" :can-forward="activeHistory.canForward" @back="restoreHistory(activeHistory.back())" @forward="restoreHistory(activeHistory.forward())" @select="index=>restoreHistory(activeHistory.select(index))" @close="historyOpen=false" />
  <div v-if="previewSnapshot" role="status">{{temporaryLabels.temporary}} <button @click="returnFromPreview">{{temporaryLabels.back}}</button></div>
  <div class="parallel-controls"><label>{{text.parallel.mode}}<select v-model="mode" :disabled="!!previewSnapshot"><option value="interleaved">{{text.parallel.interleaved}}</option><option value="panes">{{text.parallel.panes}}</option></select></label><button :disabled="!!previewSnapshot" @click="swap">{{labels.swap}}</button>
    <button v-if="mode==='panes'" :aria-pressed="state.sync" @click="toggleSync">{{state.sync?labels.sync:labels.independent}}</button>
    <button v-for="id in ([0,1]as const).filter(id=>!state.open[id])" :key="`${epoch}:${id}`" @click="reopen(id)">{{labels.reopen}} {{id+1}}</button>
  </div><p class="parallel-hint">{{text.parallel.numbering}} {{mode==='panes'?labels.hint:''}}</p>
  <div v-if="mode==='panes'" class="panes" :style="{gridTemplateRows:shape}">
    <template v-for="id in ([0,1]as const)" :key="`${epoch}:${id}`">
      <div v-if="id===1&&state.open.every(Boolean)" class="window-divider" role="separator" tabindex="0" aria-orientation="horizontal" :aria-label="labels.divider" :aria-valuenow="Math.round(state.ratio*100)" aria-valuemin="20" aria-valuemax="80" @pointerdown="drag" @pointermove="($event.currentTarget as HTMLElement).hasPointerCapture($event.pointerId)&&resize($event)" @keydown.up.prevent="state.ratio=windowRatio(state.ratio-.05)" @keydown.down.prevent="state.ratio=windowRatio(state.ratio+.05)">⋯</div>
      <ReadingWindow v-if="state.open[id]" :selection="selection" @strong="(number,chapter,verse)=>emit('strong',number,chapter,verse)" @study="(chapter,verse)=>emit('study',chapter,verse)" ref="windows" :id="id" :source="anchors[id]" :place="state.places[id]" :catalog="catalog" :service="service" :active="state.active===id" :follow-verse="follow[id]" :follow-request="followRequests[id]" :bookmarks="bookmarks??emptyBookmarks" :error="errors[id]" @activate="activate(id)" @navigate="navigate(id,$event)" @close="close(id)" @visible="(source,first,last,offset)=>visible(id,source,first,last,offset)" @select="(number,source)=>emit('select',number,source)" @bookmark="(source,verse)=>emit('bookmark',source,verse)" @actions="(source,verse,event)=>emit('actions',source,verse,event)" />
    </template>
  </div>
  <template v-else><ReadingWindow ref="pairPicker" :id="state.active" :source="activeSource" :place="state.places[state.active]" :catalog="catalog" :service="service" :active="true" :navigation-only="true" :bookmarks="bookmarks??emptyBookmarks" :error="errors[state.active]" @navigate="navigate(state.active,$event)" /><div class="parallel-controls"><label>{{text.parallel.translation}}<select :value="state.places[other()].code" @change="changePair(($event.target as HTMLSelectElement).value)"><option v-for="edition in catalog" :key="edition.code" :value="edition.code">{{edition.name}}</option></select></label></div>
    <p v-if="pairError" role="alert">{{text.parallel.error}} <button @click="pair">{{text.parallel.retry}}</button></p><p v-else-if="!paired||!activeSource" role="status">{{text.loading}}</p>
    <ComparisonRows v-else :selection="selection" @strong="(number,chapter,verse)=>emit('strong',number,chapter,verse)" @study="(chapter,verse)=>emit('study',chapter,verse)" :key="`${epoch}:${state.active}:${anchors[state.active]?.book.slug}:${anchors[state.active]?.chapter.number}:${paired[0]!.translation.code}`" :primary="anchors[state.active]!" :secondary="paired[0]!" :secondary-chapters="paired" :service="service" :initial-offset="state.places[state.active].offset" :initial-verse="state.places[state.active].verse" :selected-verse="selectedVerse" @chapter="move" @book="moveBook" @visible="(source,first,last,offset)=>visible(state.active,source,first,last,offset)" @select="(number,source)=>emit('select',number,source)" @actions="(source,verse,event)=>emit('actions',source,verse,event)" />
  </template>
</section></template>
<style scoped>.parallel-reading{display:flex;flex-direction:column;flex:1;min-height:0;overflow:hidden}.parallel-reading>.panes{flex:1;min-height:0}.parallel-reading :deep(.interleaved){flex:1;height:auto;min-height:0}</style>
<style scoped>.parallel-controls{display:flex;flex-wrap:wrap;gap:8px;padding:8px 0;font-size:14px;align-items:center}.parallel-controls label{flex:1;min-width:110px}.parallel-controls button{font-size:13px;padding:6px;min-height:36px}.parallel-controls select{font-size:13px;padding:6px}.parallel-controls select{max-width:100%}.parallel-hint{font-size:12px;padding:0}.panes{display:grid;min-height:230px;gap:0}.window-divider{display:grid;place-items:center;cursor:row-resize;touch-action:none;background:var(--light-blue,#e8f2fa);border-radius:6px;user-select:none}.window-divider:focus{outline:2px solid #3883c0}</style>
