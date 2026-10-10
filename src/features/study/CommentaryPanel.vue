<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BibleChapter } from '@/api/contracts'
import { apiBaseUrl } from '@/config/api'
import { createStudyApi, type CommentaryEntry, type CommentaryModule } from '@/api/study'
import { createStudyService, overlaps } from '@/services/studyService'
import { studyMessages } from '@/i18n/study'
import { useI18n } from '@/i18n'
import PrayerContent from '@/components/PrayerContent.vue'
import CommentaryAnnotations from './CommentaryAnnotations.vue'
import { prayerExcerpt } from '@/services/prayerContent'
import { studyPosition, positionCompare, canonicalStudyPosition, overlapsStudyRange, loadCommentaryRange } from '@/services/commentaryRange'
import { commentaryRulesMessages } from '@/i18n/commentaryRules'
import { readCommentarySourceRules,writeCommentarySourceRules,loadRuleCommentaries } from '@/services/commentarySourceRules'
import { createBibleApi } from '@/api/client'
import { createChapterService } from '@/services/chapterService'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { readWebChapter } from '@/services/webBibleLibrary'
const props = defineProps<{ chapter: BibleChapter; canon?: string | null; verse?: number; visibleFirst?: number; visibleLast?: number }>()
const { language, messages } = useI18n(), text = computed(() => studyMessages[language.value])
const service = createStudyService(createStudyApi({ baseUrl: apiBaseUrl }))
const chapterService = createChapterService(createBibleApi({ baseUrl: apiBaseUrl }), createIndexedDbChapterRepository())
const rulesText = computed(() => commentaryRulesMessages[language.value])
const ruleKey = computed(() => `bible-desktop:commentary-book-sources:${props.chapter.verses[0]?.osis_ref.split('.')[0]}`)
const bookRule = ref(false), rangeRules=ref(readCommentarySourceRules())
const canonicalLoaded=ref<{first:{chapter:number;verse:number};last:{chapter:number;verse:number}}>()
const modules = ref<CommentaryModule[]>([]), selected = ref<string[]>([])
const sourceQuery = ref('')
const foundSources = computed(() => modules.value.filter(s => `${s.name} ${s.short_name ?? ''}`.toLocaleLowerCase().includes(sourceQuery.value.trim().toLocaleLowerCase())))
try { const saved: unknown = JSON.parse(localStorage.getItem('bible-desktop:commentary-sources') ?? '[]'); if (Array.isArray(saved)) selected.value = saved.filter((v): v is string => typeof v === 'string').slice(0, 30) } catch { /* Corrupted preferences do not prevent choosing sources. */ }
const mode = ref<'book' | 'chapter' | 'verse' | 'range' | 'visible'>('verse'), first = ref(String(props.verse ?? props.chapter.verses[0]?.number ?? 1)), last = ref(first.value)
const entries = ref<CommentaryEntry[]>([]), total = ref(0), offset = ref(0), busy = ref(false), error = ref(false), retry = ref(0), expanded = ref(new Set<number>())
const bounds = computed(() => mode.value === 'visible' ? [props.visibleFirst ?? Number(first.value), props.visibleLast ?? Number(last.value)] : mode.value === 'verse' ? [props.verse ?? props.visibleFirst ?? Number(first.value), props.verse ?? props.visibleFirst ?? Number(first.value)] : [Number(first.value), Number(last.value)])
const rangeFirst = computed(() => studyPosition(first.value, props.chapter.chapter.number)), rangeLast = computed(() => studyPosition(last.value, props.chapter.chapter.number))
const valid = computed(() => ['chapter', 'book'].includes(mode.value) || (mode.value === 'range' ? Boolean(rangeFirst.value && rangeLast.value && positionCompare(rangeFirst.value, rangeLast.value) <= 0 && rangeLast.value.chapter <= props.chapter.book.chapters_count && [rangeFirst.value, rangeLast.value].every(p => p.chapter !== props.chapter.chapter.number || props.chapter.verses.some(v => v.number === p.verse))) : bounds.value[0]! <= bounds.value[1]! && bounds.value.every(v => props.chapter.verses.some(s => s.number === v))))
const canonicalChapter=computed(()=>{const osis=props.chapter.verses.find(v=>v.number===(props.verse??props.visibleFirst))?.osis_ref??props.chapter.verses[0]?.osis_ref;return osis?canonicalStudyPosition(osis).chapter:undefined})
const filtered = computed(() => ['book', 'chapter','range'].includes(mode.value) || canonicalLoaded.value ? entries.value : canonicalChapter.value?entries.value.filter(e => overlaps(e, canonicalChapter.value!, bounds.value[0]!, bounds.value[1]!)):[])
const identity = computed(() => `${mode.value}:${bounds.value.join(',')}:${JSON.stringify(rangeRules.value)}:${props.chapter.translation.code}:${props.chapter.book.slug}:${props.chapter.chapter.number}:${canonicalChapter.value}:${mode.value === 'book'}:${selected.value.join(',')}:${mode.value === 'range' ? `${first.value}:${last.value}` : ''}:${new Set(props.chapter.verses.map(v=>canonicalStudyPosition(v.osis_ref).chapter)).size>1?`${mode.value}:${bounds.value.join(',')}`:''}`)
function readSources(key: string) { try { const value: unknown = JSON.parse(localStorage.getItem(key) ?? '[]'); return Array.isArray(value) ? value.filter((s): s is string => typeof s === 'string').slice(0, 30) : [] } catch { return [] } }
watch(ruleKey, key => { bookRule.value = localStorage.getItem(key) !== null; selected.value = readSources(bookRule.value ? key : 'bible-desktop:commentary-sources') }, { immediate: true })
function saveRule() { bookRule.value = true; localStorage.setItem(ruleKey.value, JSON.stringify(selected.value)) }
function useDefault() { localStorage.removeItem(ruleKey.value); bookRule.value = false; selected.value = readSources('bible-desktop:commentary-sources') }
watch(selected, v => {localStorage.setItem(bookRule.value ? ruleKey.value : 'bible-desktop:commentary-sources', JSON.stringify(v));window.dispatchEvent(new Event('commentary-sources-changed'))}, { deep: true })
watch(identity, () => { offset.value = 0; entries.value = []; expanded.value = new Set();canonicalLoaded.value=undefined })
watch([identity, offset, retry], async (_, __, cleanup) => {
  let stale = false; cleanup(() => { stale = true }); busy.value = true; error.value = false
  try {
    const available = await service.modules(); if (stale) return; modules.value = available
    if ((!selected.value.length && (mode.value === 'book' || !rangeRules.value.some(r=>r.book===props.chapter.verses[0]?.osis_ref.split('.')[0]))) || !valid.value) { entries.value = []; total.value = 0; return }
    const canon = props.canon
    const osis = props.chapter.verses[0]?.osis_ref.split('.')[0]
    if (!canon || !osis) throw new Error('Canonical identity unavailable')
    const slug = await service.canonicalSlug(canon, osis)
    if (mode.value === 'range' && rangeFirst.value && rangeLast.value) {
      const endpoints=[]
      for (const position of [rangeFirst.value, rangeLast.value]) { const chapter = position.chapter === props.chapter.chapter.number ? props.chapter : await readWebChapter(chapterService, props.chapter.translation.code, props.chapter.book.slug, position.chapter); const verse=chapter.verses.find(v => v.number === position.verse);if(!verse)throw new Error('Missing range endpoint');endpoints.push(canonicalStudyPosition(verse.osis_ref)) }
      const start=endpoints[0]!,end=endpoints[1]!;if(positionCompare(start,end)>0)throw new Error('Canonical range unavailable')
      const data = await loadRuleCommentaries(osis,start,end,selected.value,rangeRules.value,(chapter,offset,sources)=>service.commentaries(slug,chapter,sources,offset),()=>stale)
      if (!stale) { entries.value = data; total.value = data.length;canonicalLoaded.value={first:start,last:end} } return
    }
    const sourcePositions=props.chapter.verses.filter(v=>mode.value==='chapter'||v.number>=bounds.value[0]!&&v.number<=bounds.value[1]!).map(v=>canonicalStudyPosition(v.osis_ref))
    if(mode.value!=='book' && (new Set(sourcePositions.map(p=>p.chapter)).size>1||rangeRules.value.some(r=>r.book===osis))){const ordered=[...sourcePositions].sort(positionCompare);const start=ordered[0]!,end=ordered.at(-1)!;const data=await loadRuleCommentaries(osis,start,end,selected.value,rangeRules.value,(chapter,offset,sources)=>service.commentaries(slug,chapter,sources,offset),()=>stale);if(!stale){entries.value=data;total.value=data.length;canonicalLoaded.value={first:start,last:end}}return}
    if(mode.value!=='book'&&!canonicalChapter.value)throw new Error('Canonical chapter unavailable')
    const page = await service.commentaries(slug, mode.value === 'book' ? null : canonicalChapter.value!, selected.value, offset.value)
    if (stale) return
    entries.value = offset.value ? [...new Map([...entries.value, ...page.entries].map(e => [e.id, e])).values()] : page.entries
    total.value = page.total
  } catch { if (!stale) error.value = true } finally { if (!stale) busy.value = false }
}, { immediate: true })
function toggle(id: number) { const value = new Set(expanded.value); if (value.has(id)) value.delete(id); else value.add(id); expanded.value = value }
function saveRangeRule(){
 const osis=props.chapter.verses[0]?.osis_ref.split('.')[0];if(!osis||!valid.value||!selected.value.length||mode.value==='book')return
 const positions=props.chapter.verses.filter(v=>mode.value==='chapter'||v.number>=bounds.value[0]!&&v.number<=bounds.value[1]!).map(v=>canonicalStudyPosition(v.osis_ref)).sort(positionCompare)
 const interval=canonicalLoaded.value??(positions.length?{first:positions[0]!,last:positions.at(-1)!}:undefined);if(!interval)return
 rangeRules.value=[...rangeRules.value,{id:crypto.randomUUID(),book:osis,...interval,sources:[...selected.value]}];writeCommentarySourceRules(rangeRules.value)
}
function removeRangeRule(id:string){rangeRules.value=rangeRules.value.filter(rule=>rule.id!==id);writeCommentarySourceRules(rangeRules.value)}
</script>
<template>
  <section class="commentaries">
    <h2>{{ text.commentaries }} · {{ chapter.book.name }} {{ chapter.chapter.number }}</h2>
    <details><summary>{{ text.sources }}</summary><p>{{ text.choose }}</p><input v-model="sourceQuery" type="search" :aria-label="text.sources" :placeholder="text.sources" /><div class="source-list"><label v-for="source in foundSources" :key="source.code" class="source"><input v-model="selected" type="checkbox" :value="source.code" :disabled="selected.length >= 30 && !selected.includes(source.code)" />{{ source.name }}</label></div></details>
    <p v-if="bookRule">{{ rulesText.bookRule }}</p><button @click="saveRule">{{ rulesText.saveRule }}</button><button v-if="bookRule" @click="useDefault">{{ rulesText.useDefault }}</button>
    <button v-if="mode!=='book'" :disabled="!valid||busy||!selected.length||(mode==='range'&&!canonicalLoaded)" @click="saveRangeRule">{{rulesText.saveRange}}</button>
    <details v-if="rangeRules.some(rule=>rule.book===chapter.verses[0]?.osis_ref.split('.')[0])"><summary>{{rulesText.ranges}}</summary><p v-for="rule in rangeRules.filter(rule=>rule.book===chapter.verses[0]?.osis_ref.split('.')[0])" :key="rule.id">{{rule.book}} {{rule.first.chapter}}:{{rule.first.verse}}–{{rule.last.chapter}}:{{rule.last.verse}} · {{rule.sources.map(code=>prayerExcerpt(modules.find(m=>m.code===code)?.name??code)).join(', ')}} <button @click="removeRangeRule(rule.id)">{{rulesText.removeRange}}</button></p></details>
    <label>{{ text.commentaries }}<select v-model="mode"><option v-for="value in (['book', 'chapter', 'verse', 'range', 'visible'] as const)" :key="value" :value="value">{{ text[value] }}</option></select></label>
    <div v-if="mode === 'range'" class="range"><label>{{ text.start }}<input v-model="first" :placeholder="rulesText.position" inputmode="text" /></label><label>{{ text.end }}<input v-model="last" :placeholder="rulesText.position" inputmode="text" /></label></div>
    <p v-if="!valid" role="alert">{{ rulesText.invalid }}</p><p v-else-if="mode === 'range' && rangeFirst && rangeLast">{{ chapter.book.name }} {{ rangeFirst.chapter }}:{{ rangeFirst.verse }}–{{ rangeLast.chapter }}:{{ rangeLast.verse }}</p><p v-else-if="!['book','chapter'].includes(mode)">{{ chapter.book.name }} {{ chapter.chapter.number }}:{{ bounds[0] }}<template v-if="bounds[1] !== bounds[0]">–{{ bounds[1] }}</template></p>
    <p v-if="busy" role="status">{{ messages.loading }}</p><p v-if="error" role="alert">{{ text.error }} <button @click="retry++">{{ text.retry }}</button></p>
    <p v-if="!selected.length&&!entries.length">{{ text.choose }}</p>
    <template v-if="valid">
      <article v-for="entry in filtered" :key="entry.id" class="commentary-entry">
        <h3>{{ entry.title || text.section }}</h3><p>{{ entry.author }} · {{ entry.module_name }}</p>
        <small v-if="entry.chapter_from">{{ chapter.book.name }} {{ entry.chapter_from }}<template v-if="entry.verse_from">:{{ entry.verse_from }}</template><template v-if="entry.chapter_to || entry.verse_to">–{{ entry.chapter_to ?? entry.chapter_from }}<template v-if="entry.verse_to">:{{ entry.verse_to }}</template></template></small>
        <PrayerContent v-if="expanded.has(entry.id)" :content="entry.body" /><CommentaryAnnotations v-if="expanded.has(entry.id)" :annotations="entry.annotations" :source="entry.module_name" :module-code="entry.module_code" :translation-code="chapter.translation.code"/><p v-else>{{ prayerExcerpt(entry.body) }}…</p>
        <button :aria-expanded="expanded.has(entry.id)" @click="toggle(entry.id)">{{ expanded.has(entry.id) ? text.close : text.full }}</button>
        <RouterLink v-if="entry.commentary_book_id" :to="`/books/${entry.commentary_book_id}?section=${entry.id}`">{{ text.books }}</RouterLink>
      </article>
      <p v-if="selected.length && !busy && !error && !filtered.length && entries.length >= total">{{ text.empty }}</p>
    </template>
    <button v-if="entries.length < total" :disabled="busy" @click="offset += 10">{{ text.more }} ({{ entries.length }}/{{ total }})</button><p class="status">{{ text.offline }}</p><details><summary>?</summary><p>{{ text.guide }}</p></details>
    <button :disabled="busy" @click="offset = 0; entries = []; retry++">{{ text.retry }}</button>
  </section>
</template>
<style scoped>.commentaries { margin-top:16px; padding:12px; border:1px solid var(--line); border-radius:12px }.source-list { max-height:280px; overflow:auto }.source { display:flex; align-items:center; gap:10px; padding:8px }.source input { width:20px; height:20px } select { display:block; width:100% } .range { display:flex; gap:8px }.range label { min-width:0 }.range input { width:100% }.commentary-entry { padding:12px 0; border-bottom:1px solid var(--line); white-space:pre-wrap; overflow-wrap:anywhere } .commentary-entry button,.commentary-entry a { margin:8px } </style>
