<script setup lang="ts">
import{computed,ref,watch}from'vue'
import type{BibleBook,BibleChapter,BibleVerse,TranslationSummary}from'@/api/contracts'
import type{ChapterService}from'@/services/chapterService'
import type{WindowPlace}from'@/services/readerWindows'
import{webBibleBooks}from'@/services/webBibleLibrary'
import{bibleApi}from'@/api'
import{readerWindowMessages}from'@/i18n/readerWindows'
import{useI18n}from'@/i18n'
import ContinuousReading from'./ContinuousReading.vue'
const props=defineProps<{id:number;source?:BibleChapter;place:WindowPlace;catalog:TranslationSummary[];service:ChapterService;active:boolean;followVerse?:number;followRequest?:number;bookmarks:Set<string>;error?:string;navigationOnly?:boolean}>()
const emit=defineEmits<{activate:[];navigate:[place:WindowPlace];close:[];visible:[chapter:BibleChapter,first:BibleVerse,last:BibleVerse,offset:number];select:[number:number,source:BibleChapter];bookmark:[source:BibleChapter,verse:BibleVerse];actions:[source:BibleChapter,verse:BibleVerse,event?:MouseEvent]}>()
const{language,messages:text}=useI18n(),labels=computed(()=>readerWindowMessages[language.value])
const choosing=ref(false),draftCode=ref(props.place.code),draftBook=ref(props.place.book),position=ref(`${props.place.chapter}:${props.place.verse||1}`),books=ref<BibleBook[]>([]),invalid=ref(false)
watch(()=>props.place.code,code=>{if(!choosing.value)draftCode.value=code})
watch(draftCode,async(code,_,cleanup)=>{let stale=false;cleanup(()=>stale=true);try{const value=await webBibleBooks(bibleApi,code);if(stale)return;books.value=value;if(!value.some(book=>book.slug===draftBook.value)){const osis=props.source?.verses[0]?.osis_ref.split('.')[0];draftBook.value=value.find(book=>book.canonical_book?.osis_code===osis)?.slug??''}}catch{if(!stale)books.value=[]}},{immediate:true})
function openPicker(){draftCode.value=props.place.code;draftBook.value=props.place.book;position.value=`${props.place.chapter}:${props.place.verse||1}`;choosing.value=!choosing.value;emit('activate')}
function go(){const match=/^(\d+)(?::(\d+))?$/u.exec(position.value.trim()),book=books.value.find(item=>item.slug===draftBook.value);const chapter=Number(match?.[1]),verse=Number(match?.[2]??1);invalid.value=!match||!book||chapter<1||chapter>book.chapters_count||verse<1;if(!invalid.value){emit('navigate',{code:draftCode.value,book:draftBook.value,chapter,verse,offset:0});choosing.value=false}}
defineExpose({openPicker})
</script>
<template><section class="reading-window" :class="{active}" :data-window="id" @pointerdown="emit('activate')" @focusin="emit('activate')">
  <header class="window-heading"><strong :title="`${source?.translation.name} · ${source?.book.name} ${place.chapter}:${place.verse||1}`"><span class="window-edition">{{source?.translation.name??catalog.find(e=>e.code===place.code)?.name}}</span>{{source?.book.name}} {{place.chapter}}:{{place.verse||1}}<small v-if="active"> · {{labels.active}}</small></strong><button @click="openPicker">{{labels.place}}</button><button v-if="!navigationOnly" :aria-label="`${labels.close} ${id+1}`" @click="emit('close')">×</button></header>
  <form v-if="choosing" class="window-picker" @submit.prevent="go">
    <label>{{text.translation}}<select v-model="draftCode"><option v-for="edition in catalog" :key="edition.code" :value="edition.code">{{edition.name}}</option></select></label>
    <label>{{text.book}}<select v-model="draftBook"><option v-for="item in books" :key="item.slug" :value="item.slug">{{item.name}}</option></select></label>
    <label>{{labels.place}}<input v-model="position" inputmode="numeric" pattern="[0-9]+(:[0-9]+)?" maxlength="14" /></label>
    <button>{{labels.go}}</button><p v-if="invalid" role="alert">{{labels.error}}</p>
  </form>
  <p v-if="error" role="alert">{{error}}</p>
  <ContinuousReading v-if="source&&!navigationOnly" :key="`${source.translation.code}:${source.book.slug}:${source.chapter.number}`" :initial="source" :service="service" :initial-verse="place.verse" :initial-offset="place.offset" :follow-verse="followVerse" :follow-request="followRequest" :bookmarks="bookmarks" @visible="(chapter,first,last,offset)=>emit('visible',chapter,first,last,offset)" @select="(chapter,verse)=>emit('select',verse.number,chapter)" @bookmark="(chapter,verse)=>emit('bookmark',chapter,verse)" @actions="(chapter,verse,event)=>emit('actions',chapter,verse,event)" />
  <p v-else-if="!source&&!error" role="status">{{text.loading}}</p>
</section></template>
<style scoped>.reading-window{display:flex;flex-direction:column;min-height:0;overflow:hidden;border:1px solid var(--line);border-radius:8px}.window-heading{display:flex;align-items:center;gap:6px;padding:6px 10px;background:var(--white);font-size:12px}.window-heading strong{flex:1;min-width:0}.window-edition{display:block;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;font-size:10px}.window-heading button{font-size:12px;padding:4px;min-height:28px}.active>.window-heading{background:var(--light-blue,#e8f2fa);box-shadow:inset 3px 0 #3883c0}.window-picker{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px;padding:8px;font-size:12px}.window-picker input,.window-picker select{width:100%;min-width:0}.reading-window :deep(.continuous-scroll){flex:1;height:auto;min-height:0}</style>
