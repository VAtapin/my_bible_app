<script setup lang="ts">
import {computed,nextTick,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {azbukaPath} from '../integration'
import {useI18n} from '../i18n'
import {useProfileStore} from '../stores/profile'
import {dictionaryMessages} from '../i18nDictionary'
import {dictionaryBrowse,dictionaryCatalogQuery} from '../services/dictionaryNavigation'
import {createSlavonicDictionary,DictionaryFailure,type DictionaryEntry,type DictionaryLetter,type DictionarySearchMode} from '../services/slavonicDictionary'
import {loadDictionaryNotebook,rememberDictionaryWord,toggleDictionaryFavorite,type DictionaryNotebook} from '../storage/dictionaryNotebook'

const route=useRoute(),router=useRouter(),profile=useProfileStore(),{locale,t}=useI18n(),labels=computed(()=>dictionaryMessages[locale.value])
const api=createSlavonicDictionary(),q=ref(''),mode=ref<DictionarySearchMode>('word'),letter=ref<number>(),alphabet=ref<DictionaryLetter[]>([])
const entries=ref<DictionaryEntry[]>([]),total=ref(0),loading=ref(false),failed=ref(''),article=ref<DictionaryEntry>(),articleFailure=ref(''),saveFailed=ref(false)
const notebook=ref<DictionaryNotebook>({favorites:[],recent:[]}),tab=ref<'catalog'|'search'|'favorites'|'recent'>('catalog'),dialog=ref<HTMLElement>()
const entryKey=computed(()=>typeof route.query.entry==='string'?route.query.entry:'')
const favorite=computed(()=>notebook.value.favorites.some(word=>word.key===article.value?.key))
let searchRequest:AbortController|undefined,articleRequest:AbortController|undefined,lastFocus:HTMLElement|undefined
function failure(error:unknown){return error instanceof DictionaryFailure?(error.kind==='network'?'offline':error.status===429?'rate':'unavailable'):'unavailable'}
async function load(offset=0){
 searchRequest?.abort();const request=new AbortController();searchRequest=request;loading.value=true;failed.value=''
 if(offset===0){entries.value=[];total.value=0}
 try{const browse=dictionaryBrowse(route.query)
  const result=await api.search(browse.q,browse.mode,offset,browse.letter,request.signal);if(request.signal.aborted)return
  entries.value=offset?[...entries.value,...result.data]:result.data;total.value=result.total
 }catch(error){if(!request.signal.aborted)failed.value=failure(error)}finally{if(searchRequest===request)loading.value=false}
}
function submit(){tab.value='search';const query={view:'search',...(q.value.trim()?{q:q.value.trim()}:{}),mode:mode.value,...(letter.value?{letter:String(letter.value)}:{})};
 if(route.query.q===query.q&&route.query.mode===query.mode&&route.query.letter===('letter' in query?query.letter:undefined))void load();else void router.push({path:azbukaPath('/dictionary'),query})
}
function chooseMode(value:DictionarySearchMode){if(mode.value!==value){mode.value=value;submit()}}
function browseCatalog(section?:number){tab.value='catalog';void router.push({path:azbukaPath('/dictionary'),query:dictionaryCatalogQuery(section)})}
function chooseTab(value:typeof tab.value){if(value==='catalog')browseCatalog();else if(value==='search'){letter.value=undefined;submit()}else tab.value=value}
watch(()=>[route.query.view,route.query.q,route.query.mode,route.query.letter].join('|'),()=>{
 const browse=dictionaryBrowse(route.query);q.value=browse.q;mode.value=browse.mode;letter.value=browse.letter;tab.value=browse.catalog?'catalog':'search'
 void load()
},{immediate:true})
function open(key:string){lastFocus=document.activeElement as HTMLElement;void router.push({query:{...route.query,entry:key}})}
function close(){void router.replace({query:{...route.query,entry:undefined}})}
watch(entryKey,async key=>{
 articleRequest?.abort();article.value=undefined;articleFailure.value=''
 if(!key){await nextTick();lastFocus?.focus();return}
 const request=new AbortController();articleRequest=request
 await nextTick();dialog.value?.focus()
 try{const value=entries.value.find(entry=>entry.key===key)??await api.article(key,request.signal);if(request.signal.aborted)return;article.value=value
  try{notebook.value=await rememberDictionaryWord({key:value.key,word:value.headword,civil:value.civil})}catch{saveFailed.value=true}
 }catch(error){if(!request.signal.aborted)articleFailure.value=failure(error)}
},{immediate:true})
async function save(){if(!article.value)return;try{notebook.value=await toggleDictionaryFavorite({key:article.value.key,word:article.value.headword,civil:article.value.civil});saveFailed.value=false}catch{saveFailed.value=true}}
void loadDictionaryNotebook().then(value=>{notebook.value=value}).catch(()=>{saveFailed.value=true})
const alphabetRequest=new AbortController()
void api.alphabet(alphabetRequest.signal).then(value=>{alphabet.value=value}).catch(()=>{/* Search remains available if the alphabet request fails. */})
function dialogKeys(event:KeyboardEvent){if(event.key==='Escape'){event.preventDefault();close()}
 if(event.key==='Tab'){const nodes=dialog.value?.querySelectorAll<HTMLElement>('button,a[href]');if(!nodes?.length)return
 const first=nodes[0]!,last=nodes[nodes.length-1]!;if(event.shiftKey&&(document.activeElement===first||document.activeElement===dialog.value)){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}
}
onUnmounted(()=>{searchRequest?.abort();articleRequest?.abort();alphabetRequest.abort()})
</script>

<template><main class="page dictionary-page">
 <section :inert="entryKey?true:undefined">
  <header class="dictionary-heading"><RouterLink :to="azbukaPath(profile.profile?'/':'/onboarding')" class="back-button" :aria-label="t('home')">←</RouterLink><div><h1>{{labels.title}}</h1><p>{{labels.intro}}</p></div></header>
  <nav class="dictionary-tabs"><button v-for="value in (['catalog','search','favorites','recent'] as const)" :key="value" :aria-pressed="tab===value" @click="chooseTab(value)">{{labels[value]}}</button></nav>
  <template v-if="tab==='search'||tab==='catalog'">
   <nav v-if="tab==='catalog'" class="dictionary-letters" :aria-label="labels.alphabet"><button :aria-pressed="!letter" @click="browseCatalog()">{{labels.all}}</button><button v-for="item in alphabet" :key="item.chapter" :aria-pressed="letter===item.chapter" :aria-label="`${item.label} · ${item.count}`" @click="browseCatalog(item.chapter)"><span lang="cu">{{item.label}}</span></button></nav>
   <form v-else class="dictionary-form" @submit.prevent="submit">
    <div class="dictionary-modes"><button type="button" :aria-pressed="mode==='word'" @click="chooseMode('word')">{{labels.word}}</button><button type="button" :aria-pressed="mode==='meaning'" @click="chooseMode('meaning')">{{labels.meaning}}</button></div>
    <div class="dictionary-query"><input v-model="q" type="search" maxlength="120" :aria-label="labels.placeholder" :placeholder="labels.placeholder" autocomplete="off"><button type="submit" :disabled="loading">{{labels.search}}</button></div>
    <label v-if="alphabet.length" class="dictionary-alphabet">{{labels.alphabet}} <select v-model="letter" @change="submit"><option :value="undefined">{{labels.all}}</option><option v-for="item in alphabet" :key="item.chapter" :value="item.chapter">{{item.label}} · {{item.count}}</option></select></label>
   </form>
   <p v-if="failed" role="alert">{{labels[failed as 'offline'|'rate'|'unavailable']}}</p>
   <p v-if="loading" role="status">{{labels.loading}}</p>
   <p v-else-if="!failed" class="dictionary-count">{{tab==='catalog'?labels.words:labels.found}}: {{total}}</p>
   <ol class="dictionary-results" :class="{'dictionary-catalog':tab==='catalog'}"><li v-for="entry in entries" :key="entry.key"><button @click="open(entry.key)"><strong lang="cu">{{entry.headword}}</strong><small lang="ru">{{entry.civil}}</small><p v-if="tab==='search'" lang="ru">{{entry.definition.slice(0,200)}}{{entry.definition.length>200?'…':''}}</p></button></li></ol>
   <p v-if="!loading&&!failed&&!entries.length">{{labels.empty}}</p>
   <button v-if="entries.length<total" class="secondary-button" :disabled="loading" @click="load(entries.length)">{{labels.more}}</button>
  </template>
  <template v-else><p v-if="!notebook[tab].length">{{labels.notebookEmpty}}</p><ol class="dictionary-results"><li v-for="word in notebook[tab]" :key="word.key"><button @click="open(word.key)"><strong lang="cu">{{word.word}}</strong><small lang="ru">{{word.civil}}</small></button></li></ol></template>
  <p v-if="saveFailed" role="alert">{{labels.saveFailed}}</p>
 </section>
 <div v-if="entryKey" class="dictionary-overlay" @click.self="close"><section ref="dialog" class="dictionary-article" role="dialog" aria-modal="true" :aria-label="article?.headword??labels.title" tabindex="-1" @keydown="dialogKeys">
  <header><button @click="close">← {{labels.back}}</button><button :aria-label="labels.close" @click="close">×</button></header>
  <p v-if="articleFailure" role="alert">{{labels[articleFailure as 'offline'|'rate'|'unavailable']}}</p><p v-else-if="!article" role="status">{{labels.loading}}</p>
  <template v-if="article"><h1 lang="cu">{{article.headword}}</h1><p v-if="article.civil" lang="ru">{{article.civil}}</p><button class="dictionary-save" :aria-pressed="favorite" @click="save">{{favorite?'★':'☆'}} {{favorite?labels.remove:labels.add}}</button>
   <div class="dictionary-definition" lang="ru">{{article.definition}}</div>
  </template>
 </section></div>
</main></template>

<style scoped>
.dictionary-page{padding-top:16px}.dictionary-heading{display:flex;align-items:flex-start;gap:12px}.dictionary-heading h1{font-size:clamp(1.5rem,5vw,2.3rem);margin-bottom:6px}.dictionary-heading p{font-size:14px;color:var(--ink-soft)}.dictionary-tabs,.dictionary-modes{display:flex;gap:6px;flex-wrap:wrap}.dictionary-tabs{margin-bottom:16px}.dictionary-tabs button,.dictionary-modes button{border:1px solid var(--line);border-radius:10px;padding:7px 12px;background:white;cursor:pointer}.dictionary-tabs button[aria-pressed=true],.dictionary-modes button[aria-pressed=true]{background:var(--ink-soft);color:white}.dictionary-form{display:grid;gap:10px}.dictionary-query{display:flex;gap:6px}.dictionary-query input{min-width:0;flex:1;padding:10px 12px;border:1px solid var(--line);border-radius:12px;background:white;font-family:Georgia,serif}.dictionary-query button{border:0;border-radius:12px;padding:10px 16px;background:var(--ink-soft);color:white;cursor:pointer}.dictionary-alphabet{display:flex;align-items:center;gap:10px;font-size:14px}.dictionary-alphabet select{max-width:100%;padding:6px;border:1px solid var(--line);border-radius:8px}.dictionary-count{font-size:13px;margin:14px 0 4px}.dictionary-results{padding:0;margin:0;list-style:none}.dictionary-results li{border-bottom:1px solid var(--line)}.dictionary-results button{width:100%;text-align:left;padding:12px 2px;border:0;background:transparent;cursor:pointer}.dictionary-results strong{display:block;font:26px/1.4 Ponomar,Georgia,serif}.dictionary-results small{font:13px/1.5 sans-serif;color:var(--ink-soft)}.dictionary-results p{margin:5px 0 0;font:15px/1.5 Georgia,serif}.dictionary-overlay{position:fixed;inset:0;z-index:100;background:#17344d66;display:flex;justify-content:center}.dictionary-article{width:100%;max-width:1080px;height:100%;padding:14px 16px 30px;background:var(--paper);overflow:auto}.dictionary-article header{display:flex;justify-content:space-between;gap:8px;position:sticky;top:-14px;padding:8px 0;background:var(--paper)}.dictionary-article button{border:1px solid var(--line);border-radius:10px;background:white;padding:7px 11px;cursor:pointer}.dictionary-article h1{font:clamp(2rem,6vw,3.2rem)/1.4 Ponomar,Georgia,serif;margin:16px 0 5px}.dictionary-definition{white-space:pre-wrap;overflow-wrap:anywhere;font:19px/1.65 Georgia,serif;margin:20px 0}.dictionary-article p[lang=ru]{font-family:Georgia,serif}.dictionary-save{font:14px sans-serif!important}@media(max-width:600px){.dictionary-page{padding:12px 12px 115px}.dictionary-article{padding-inline:12px}}
</style>
<style scoped>.dictionary-article{overscroll-behavior:contain}.dictionary-query button:disabled{opacity:.6;cursor:wait}.dictionary-results button,.dictionary-article h1{overflow-wrap:anywhere}</style>
<style scoped>.dictionary-tabs{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:4px}.dictionary-tabs button{padding:7px 4px;font-size:12px;min-width:0}.dictionary-letters{display:flex;flex-wrap:wrap;gap:5px}.dictionary-letters button{min-width:34px;border:1px solid var(--line);border-radius:8px;background:white;padding:4px 7px;cursor:pointer}.dictionary-letters span{font:22px/1.2 Ponomar,Georgia,serif}.dictionary-letters button[aria-pressed=true]{background:var(--ink-soft);color:white}.dictionary-catalog button{display:flex;align-items:baseline;gap:12px;padding:9px 2px}.dictionary-catalog small{margin-left:auto;text-align:right}</style>
