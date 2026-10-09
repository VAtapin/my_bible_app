<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { enabledWebBibles, webBibleBooks } from '@/services/webBibleLibrary'
import { searchVersePage, searchSegments, type SearchMatch, type SearchScope } from '@/services/verseSearch'
import { bibleApi } from '@/api'
import type { BibleBook, TranslationSummary, VerseSearchResult } from '@/api/contracts'
import { useI18n } from '@/i18n'
import { verseSearchMessages, morphologyMessages } from '@/i18n/verseSearch'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import {prepareSearchStemming} from '@/services/searchStemming'
const route = useRoute(), router = useRouter(), { language, messages: text } = useI18n()
const labels = computed(() => ({...verseSearchMessages[language.value],...morphologyMessages[language.value]}))
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const codes = ref<string[]>(typeof route.query.translation === 'string' ? route.query.translation.split(',') : [])
const match = ref<SearchMatch>(['exact','phrase','partial','strong','morphology'].includes(String(route.query.match)) ? route.query.match as SearchMatch : 'exact')
const scope = ref<SearchScope>(['all','old','new','psalms'].includes(String(route.query.scope)) ? route.query.scope as SearchScope : 'all')
const book=ref(typeof route.query.book==='string'?route.query.book:''), books=ref<BibleBook[]>([])
watch(codes,async(value,_,cleanup)=>{let stale=false;cleanup(()=>stale=true);try{const values=await Promise.all(value.map(code=>webBibleBooks(bibleApi,code)));if(!stale)books.value=[...new Map(values.flat().filter(item=>item.canonical_book?.osis_code).map(item=>[item.canonical_book!.osis_code,item])).values()]}catch{if(!stale)books.value=[]}},{deep:true,immediate:true})
const editions = ref<TranslationSummary[]>([]), results = ref<VerseSearchResult[]>([]), busy = ref(false), message = ref(''), local = ref(false)
const cursors = ref<Record<string, number>>({}), pending = ref<string[]>([])
let generation = 0
const active = ref({query:'', codes:[] as string[], match:'exact' as SearchMatch, scope:'all' as SearchScope,book:''})
const cacheKey = () => `verse-search:v2:${JSON.stringify(active.value)}`
function saveResults() {
  // Vue proxies cannot be structured-cloned by IndexedDB.
  const saved=JSON.parse(JSON.stringify({results:results.value,cursors:cursors.value,pending:pending.value,local:local.value,scroll:document.querySelector('.app-content')?.scrollTop??0}))
  return writeCalendarState(cacheKey(),saved).catch(()=>{})
}
function valid() { return query.value.trim().length >= 2 && query.value.length <= 500 && codes.value.length > 0 && (match.value !== 'strong' || /^[HG]\d{1,5}$/iu.test(query.value.trim())) }
async function search() {
  if (!valid()) { message.value = labels.value.invalid; return }
  generation++; results.value=[]; cursors.value={}; pending.value=[...codes.value]; local.value=false
  active.value={query:query.value.trim(), codes:[...codes.value], match:match.value, scope:scope.value,book:book.value}
  await router.replace({path:'/search',query:{q:active.value.query,translation:active.value.codes.join(','),match:active.value.match,scope:active.value.scope,...(book.value?{book:book.value}:{})}})
  await more()
}
async function more() {
  if (busy.value) return
  const current= generation, searching={...active.value}
  busy.value=true; message.value=''
  try {
    const pageCodes=[...pending.value]
    for (const code of pageCodes) {
      const page=await searchVersePage(bibleApi,createIndexedDbChapterRepository(),code,searching.query,{match:searching.match,scope:searching.scope,offset:cursors.value[code]??0,...(searching.book?{book:searching.book}:{})})
      if (current !== generation) return
      const known=new Set(results.value.filter(row => row.translation.code===code).map(row => row.verse_id))
      results.value.push(...page.results.filter(row => !known.has(row.verse_id)))
      cursors.value[code]=page.next; local.value ||= page.local
      if (!page.more) pending.value=pending.value.filter(item => item!==code)
    }
    await saveResults()
    if (!results.value.length) message.value=pending.value.length?labels.value.progress:text.value.readerActions.noResults
  } catch { if (current===generation) message.value=text.value.readerActions.searchFailed }
  finally { if (current===generation) busy.value=false }
}
onMounted(async () => {
  try {
    editions.value=await enabledWebBibles(bibleApi)
    if (!codes.value.length) codes.value=editions.value.slice(0,1).map(item=>item.code)
    if (valid()) {
      if(match.value==='morphology')await prepareSearchStemming()
      active.value={query:query.value.trim(),codes:[...codes.value],match:match.value,scope:scope.value,book:book.value}
      const saved=await readCalendarState<{results:VerseSearchResult[];cursors:Record<string,number>;pending:string[];local:boolean;scroll?:number}>(cacheKey())
      if (saved) {
        results.value=saved.results;cursors.value=saved.cursors;pending.value=saved.pending;local.value=saved.local
        await nextTick(); const content=document.querySelector('.app-content');if(content)content.scrollTop=saved.scroll??0
      }
      else await search()
    }
  } catch { message.value=text.value.readerActions.searchFailed }
})
onBeforeUnmount(()=>{generation++;if(active.value.query)void saveResults()})
</script>
<template>
  <MobileShell back-to="/reader">
    <h1 class="bible-search-title">{{ text.readerActions.search }}</h1>
    <form class="search-options" @submit.prevent="search">
      <label>{{ text.readerActions.search }}<input v-model="query" type="search" maxlength="500" :placeholder="text.readerActions.searchHint" :disabled="busy" /></label>
      <label>{{ labels.modes }}<select v-model="match" :disabled="busy"><option v-for="mode in (['exact','phrase','partial','strong','morphology'] as const)" :key="mode" :value="mode">{{ labels[mode] }}</option></select></label>
      <label>{{ labels.scope }}<select v-model="scope" :disabled="busy"><option v-for="area in (['all','old','new','psalms'] as const)" :key="area" :value="area">{{ labels[area] }}</option></select></label>
      <label>{{ text.book }}<select v-model="book" :disabled="busy"><option value="">{{ labels.all }}</option><option v-for="item in books" :key="item.canonical_book!.osis_code" :value="item.canonical_book!.osis_code">{{ item.name }}</option></select></label>
      <fieldset :disabled="busy"><legend>{{ labels.translations }}</legend><label v-for="edition in editions" :key="edition.code" class="edition-choice"><input v-model="codes" type="checkbox" :value="edition.code" />{{ edition.name }}</label></fieldset>
      <button :disabled="busy" type="submit" class="primary-action">{{ busy?text.loading:text.readerActions.find }}</button>
    </form>
    <p class="status">{{ labels.guide }}</p><p v-if="match==='morphology'">{{ labels.morphologyHint }}</p><p v-if="local" role="status">{{ labels.local }}</p><p v-if="message" role="status">{{ message }}</p>
    <p v-if="results.length">{{ labels.found }}: {{ results.length }}</p>
    <div class="content-catalog">
      <RouterLink v-for="item in results" :key="`${item.translation.code}-${item.verse_id}`" class="search-result" :to="{path:'/reader',query:{translation:item.translation.code,book:item.book.slug,chapter:String(item.chapter_number),verse:String(item.verse_number)}}"><strong>{{ item.reference }}</strong><small v-if="active.codes.length>1"> · {{ editions.find(e=>e.code===item.translation.code)?.name ?? item.translation.code }}</small><p><template v-for="(part,index) in (active.match==='morphology'&&item.snippet_segments?.some(part=>part.match) ? item.snippet_segments : searchSegments(item.snippet,active.query,active.match,editions.find(e=>e.code===item.translation.code)?.language.code))" :key="index"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></p></RouterLink>
    </div>
    <button v-if="pending.length" :disabled="busy" @click="more">{{ busy?text.loading:labels.more }}</button>
    <p v-else-if="results.length">{{ labels.exhausted }}</p>
  </MobileShell>
</template>
<style scoped>.search-options{display:grid;gap:12px}.search-options>label{display:grid;gap:4px}.edition-choice{display:flex;gap:8px;align-items:center;margin:6px 0}fieldset{max-height:180px;overflow:auto}input,select{min-width:0;max-width:100%}mark{background:#ffeb9c;color:#242424}</style>
