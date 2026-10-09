<script setup lang="ts">
import{computed,nextTick,onMounted,onUnmounted,ref,watch}from'vue'
import type{BibleChapter,BibleVerse,TranslationSummary}from'@/api/contracts'
import{bibleApi}from'@/api'
import type{ChapterService}from'@/services/chapterService'
import{loadComparison}from'@/services/bibleComparison'
import{readWebChapter}from'@/services/webBibleLibrary'
import{readWindows,windowRatio,mergeWindowLocation,type ReaderWindows,type WindowPlace}from'@/services/readerWindows'
import{readerWindowMessages}from'@/i18n/readerWindows'
import{useI18n}from'@/i18n'
import ReadingWindow from'./ReadingWindow.vue'
import ComparisonRows from'./ComparisonRows.vue'
const props=defineProps<{primary:BibleChapter;catalog:TranslationSummary[];service:ChapterService;selectedVerse?:number;primaryOffset?:number;bookmarks?:Set<string>}>()
const emit=defineEmits<{select:[number:number,source?:BibleChapter];visible:[chapter:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number];bookmark:[source:BibleChapter,verse:BibleVerse];actions:[source:BibleChapter,verse:BibleVerse,event?:MouseEvent]}>()
const{language,messages:text}=useI18n(),labels=computed(()=>readerWindowMessages[language.value])
const key='bible-desktop:reading-windows:v1',saved=readWindows(localStorage.getItem(key))
const point=(source:BibleChapter,verse=props.selectedVerse??1,offset=0):WindowPlace=>({code:source.translation.code,book:source.book.slug,chapter:source.chapter.number,verse,offset})
const initial=point(props.primary,props.selectedVerse??1,props.primaryOffset??0),code=props.catalog.find(item=>item.code!==initial.code)?.code??initial.code
const state=ref<ReaderWindows>(saved?mergeWindowLocation(saved,initial):{places:[initial,{...initial,code}],active:0,sync:true,ratio:.5,open:[true,true]})
const mode=ref(localStorage.getItem('bible-desktop:compare-mode')==='panes'?'panes':'interleaved')
const anchors=ref<[BibleChapter?,BibleChapter?]>([]),current=ref<[BibleChapter?,BibleChapter?]>([]),errors=ref<[string?,string?]>([]),follow=ref<[number?,number?]>([])
const paired=ref<BibleChapter>(),pairError=ref(false),root=ref<HTMLElement>(),windows=ref<InstanceType<typeof ReadingWindow>[]>([]),pairPicker=ref<InstanceType<typeof ReadingWindow>>(),height=ref(380)
const request=ref([0,0]),followRequests=ref([0,0]),epoch=ref(0),emptyBookmarks=new Set<string>()
let stopped=false,lastSync='',pairRequest=0
const activeSource=computed(()=>current.value[state.value.active]??anchors.value[state.value.active])
const other=()=>state.value.active===0?1:0
const shape=computed(()=>state.value.open.every(Boolean)?`${state.value.ratio}fr 24px ${1-state.value.ratio}fr`:'1fr')
watch(state,value=>localStorage.setItem(key,JSON.stringify(value)),{deep:true})
watch(mode,value=>localStorage.setItem('bible-desktop:compare-mode',value))
async function loadPlace(id:0|1,place:WindowPlace) {
  const version=++request.value[id];errors.value[id]=undefined
  try {
    const value=place.code===props.primary.translation.code&&place.book===props.primary.book.slug&&place.chapter===props.primary.chapter.number?props.primary:await readWebChapter(props.service,place.code,place.book,place.chapter)
    if(stopped||version!==request.value[id])return
    if(value.translation.code!==place.code||value.book.slug!==place.book||value.chapter.number!==place.chapter||(place.verse>0&&!value.verses.some(v=>v.number===place.verse)))throw new Error('Wrong passage')
    state.value.places[id]={...place};anchors.value[id]=value;current.value[id]=value;follow.value[id]=place.verse
    followRequests.value[id]++
    if(id===state.value.active){emit('select',place.verse,value);const verse=value.verses.find(v=>v.number===place.verse);if(verse&&mode.value==='interleaved')emit('visible',value,verse,verse,place.offset)}
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
    const value=existing?.translation.code===code&&existing.chapter.number===source.chapter.number&&existing.verses.some(v=>v.osis_ref===first.osis_ref)?existing:code===source.translation.code?source:await loadComparison(source,code,bibleApi,props.service)
    if(stopped||version!==request.value[id])return
    const target=value.verses.find(v=>v.osis_ref===first.osis_ref&&v.plain_text.trim())
    if(!target){errors.value[id]=labels.value.missing;return}
    errors.value[id]=undefined;state.value.places[id]=point(value,target.number,0);anchors.value[id]=value;current.value[id]=value;follow.value[id]=target.number
    followRequests.value[id]++
  }catch{if(!stopped&&version===request.value[id])errors.value[id]=labels.value.missing}
}
function visible(id:0|1,source:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number) {
  current.value[id]=source;state.value.places[id]=point(source,first.number,offset)
  if(id===state.value.active){emit('visible',source,first,last,offset);if(mode.value==='panes')void synchronize(source,first)}
}
async function pair(){
  const source=activeSource.value;if(!source||mode.value!=='interleaved')return
  const version=++pairRequest;pairError.value=false;paired.value=undefined
  try {const code=state.value.places[other()].code;const value=code===source.translation.code?source:await loadComparison(source,code,bibleApi,props.service);if(!stopped&&version===pairRequest)paired.value=value}
  catch{if(!stopped&&version===pairRequest)pairError.value=true}
}
watch([mode,activeSource,()=>state.value.places[other()].code],()=>void pair())
async function navigate(id:0|1,place:WindowPlace){state.value.active=id;lastSync='';await loadPlace(id,place);void pair()}
async function move(offset:number){const id=state.value.active,place=state.value.places[id],source=activeSource.value;if(!source)return;const number=place.chapter+offset;if(number<1||number>source.book.chapters_count)return;await navigate(id,{...place,chapter:number,verse:1,offset:0})}
function activate(id:0|1){if(state.value.active===id)return;state.value.active=id;lastSync='';const source=current.value[id],verse=source?.verses.find(v=>v.number===state.value.places[id].verse);if(source&&verse){emit('select',verse.number,source);emit('visible',source,verse,verse,state.value.places[id].offset);if(mode.value==='panes')void synchronize(source,verse)}}
function swap(){epoch.value++;[state.value.places[0],state.value.places[1]]=[state.value.places[1],state.value.places[0]];[anchors.value[0],anchors.value[1]]=[anchors.value[1],anchors.value[0]];[current.value[0],current.value[1]]=[current.value[1],current.value[0]];[errors.value[0],errors.value[1]]=[errors.value[1],errors.value[0]];[follow.value[0],follow.value[1]]=[follow.value[1],follow.value[0]];state.value.active=state.value.open[other()]?other():state.value.active;lastSync='';void pair()}
async function changePair(code:string){const id=other(),source=activeSource.value;if(!source)return;try{const value=code===source.translation.code?source:await loadComparison(source,code,bibleApi,props.service);await loadPlace(id,point(value,state.value.places[state.value.active].verse));void pair()}catch{pairError.value=true}}
async function toggleSync(){state.value.sync=!state.value.sync;lastSync='';const source=activeSource.value,verse=source?.verses.find(v=>v.number===state.value.places[state.value.active].verse);if(source&&verse)await synchronize(source,verse)}
function close(id:0|1){if(!state.value.open[other()]&&id===state.value.active)return;state.value.open[id]=false;if(id===state.value.active)activate(id===0?1:0)}
async function reopen(id:0|1){state.value.open[id]=true;await loadPlace(id,state.value.places[id]);lastSync='';const source=activeSource.value,verse=source?.verses.find(v=>v.number===state.value.places[state.value.active].verse);if(source&&verse)void synchronize(source,verse)}
function resize(event:PointerEvent){const element=root.value?.querySelector('.panes');if(!element)return;const bounds=element.getBoundingClientRect();state.value.ratio=windowRatio((event.clientY-bounds.top)/bounds.height)}
function drag(event:PointerEvent){(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);resize(event)}
function fit(){const top=root.value?.querySelector('.panes')?.getBoundingClientRect().top??260;height.value=Math.max(360,window.innerHeight-top-84)}
watch([mode,()=>state.value.open.join()],async()=>{await nextTick();fit();root.value?.querySelector('.panes')?.scrollIntoView({block:'nearest'})})
onMounted(async()=>{fit();window.addEventListener('resize',fit);if(!state.value.open[state.value.active])state.value.active=other();let missingDefault=false;if(!saved&&code!==initial.code){try{const value=await loadComparison(props.primary,code,bibleApi,props.service);state.value.places[1]=point(value)}catch{errors.value[1]=labels.value.missing;missingDefault=true}}await Promise.all(([0,1]as const).filter(id=>state.value.open[id]&&!(id===1&&missingDefault)).map(id=>loadPlace(id,state.value.places[id])));await pair();await nextTick();root.value?.querySelector('.panes')?.scrollIntoView({block:'nearest'})})
onUnmounted(()=>{stopped=true;window.removeEventListener('resize',fit)})
defineExpose({move,choosePlace:()=>{const id=state.value.active;if(mode.value==='interleaved')pairPicker.value?.openPicker();else windows.value.find(window=>window.$el.dataset.window===String(id))?.openPicker()}})
</script>
<template><section ref="root" class="parallel-reading">
  <div class="parallel-controls"><label>{{text.parallel.mode}}<select v-model="mode"><option value="interleaved">{{text.parallel.interleaved}}</option><option value="panes">{{text.parallel.panes}}</option></select></label><button @click="swap">{{labels.swap}}</button>
    <button v-if="mode==='panes'" :aria-pressed="state.sync" @click="toggleSync">{{state.sync?labels.sync:labels.independent}}</button>
    <button v-for="id in ([0,1]as const).filter(id=>!state.open[id])" :key="`${epoch}:${id}`" @click="reopen(id)">{{labels.reopen}} {{id+1}}</button>
  </div><p class="parallel-hint">{{text.parallel.numbering}} {{mode==='panes'?labels.hint:''}}</p>
  <div v-if="mode==='panes'" class="panes" :style="{height:`${height}px`,gridTemplateRows:shape}">
    <template v-for="id in ([0,1]as const)" :key="`${epoch}:${id}`">
      <div v-if="id===1&&state.open.every(Boolean)" class="window-divider" role="separator" tabindex="0" aria-orientation="horizontal" :aria-label="labels.divider" :aria-valuenow="Math.round(state.ratio*100)" aria-valuemin="20" aria-valuemax="80" @pointerdown="drag" @pointermove="($event.currentTarget as HTMLElement).hasPointerCapture($event.pointerId)&&resize($event)" @keydown.up.prevent="state.ratio=windowRatio(state.ratio-.05)" @keydown.down.prevent="state.ratio=windowRatio(state.ratio+.05)">⋯</div>
      <ReadingWindow v-if="state.open[id]" ref="windows" :id="id" :source="anchors[id]" :place="state.places[id]" :catalog="catalog" :service="service" :active="state.active===id" :follow-verse="follow[id]" :follow-request="followRequests[id]" :bookmarks="bookmarks??emptyBookmarks" :error="errors[id]" @activate="activate(id)" @navigate="navigate(id,$event)" @close="close(id)" @visible="(source,first,last,offset)=>visible(id,source,first,last,offset)" @select="(number,source)=>emit('select',number,source)" @bookmark="(source,verse)=>emit('bookmark',source,verse)" @actions="(source,verse,event)=>emit('actions',source,verse,event)" />
    </template>
  </div>
  <template v-else><ReadingWindow ref="pairPicker" :id="state.active" :source="activeSource" :place="state.places[state.active]" :catalog="catalog" :service="service" :active="true" :navigation-only="true" :bookmarks="bookmarks??emptyBookmarks" :error="errors[state.active]" @navigate="navigate(state.active,$event)" /><div class="parallel-controls"><label>{{text.parallel.translation}}<select :value="state.places[other()].code" @change="changePair(($event.target as HTMLSelectElement).value)"><option v-for="edition in catalog" :key="edition.code" :value="edition.code">{{edition.name}}</option></select></label></div>
    <p v-if="pairError" role="alert">{{text.parallel.error}} <button @click="pair">{{text.parallel.retry}}</button></p><p v-else-if="!paired||!activeSource" role="status">{{text.loading}}</p>
    <ComparisonRows v-else :primary="activeSource" :secondary="paired" :selected-verse="selectedVerse" @select="(number,source)=>emit('select',number,source)" />
  </template>
</section></template>
<style scoped>.parallel-controls{display:flex;flex-wrap:wrap;gap:8px;padding:8px 0;font-size:14px;align-items:center}.parallel-controls label{flex:1;min-width:110px}.parallel-controls button{font-size:13px;padding:6px;min-height:36px}.parallel-controls select{font-size:13px;padding:6px}.parallel-controls select{max-width:100%}.parallel-hint{font-size:12px;padding:0}.panes{display:grid;min-height:230px;gap:0}.window-divider{display:grid;place-items:center;cursor:row-resize;touch-action:none;background:var(--light-blue,#e8f2fa);border-radius:6px;user-select:none}.window-divider:focus{outline:2px solid #3883c0}</style>
