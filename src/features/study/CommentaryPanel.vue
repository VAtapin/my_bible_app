<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BibleChapter } from '@/api/contracts'
import { apiBaseUrl } from '@/config/api'
import { createStudyApi, type CommentaryEntry, type CommentaryModule } from '@/api/study'
import { createStudyService, overlaps } from '@/services/studyService'
import { studyMessages } from '@/i18n/study'
import { useI18n } from '@/i18n'
import PrayerContent from '@/components/PrayerContent.vue'
import { prayerExcerpt } from '@/services/prayerContent'
const props = defineProps<{ chapter: BibleChapter; canon?: string | null; verse?: number; visibleFirst?: number; visibleLast?: number }>()
const { language, messages } = useI18n(), text = computed(() => studyMessages[language.value])
const service = createStudyService(createStudyApi({ baseUrl: apiBaseUrl }))
const modules = ref<CommentaryModule[]>([]), selected = ref<string[]>([])
const sourceQuery = ref('')
const foundSources = computed(() => modules.value.filter(s => `${s.name} ${s.short_name ?? ''}`.toLocaleLowerCase().includes(sourceQuery.value.trim().toLocaleLowerCase())))
try { const saved: unknown = JSON.parse(localStorage.getItem('bible-desktop:commentary-sources') ?? '[]'); if (Array.isArray(saved)) selected.value = saved.filter((v): v is string => typeof v === 'string').slice(0, 30) } catch { /* Corrupted preferences do not prevent choosing sources. */ }
const mode = ref<'book' | 'chapter' | 'verse' | 'range' | 'visible'>('verse'), first = ref(props.verse ?? props.chapter.verses[0]?.number ?? 1), last = ref(first.value)
const entries = ref<CommentaryEntry[]>([]), total = ref(0), offset = ref(0), busy = ref(false), error = ref(false), retry = ref(0), expanded = ref(new Set<number>())
const bounds = computed(() => mode.value === 'visible' ? [props.visibleFirst ?? first.value, props.visibleLast ?? last.value] : mode.value === 'verse' ? [props.verse ?? props.visibleFirst ?? first.value, props.verse ?? props.visibleFirst ?? first.value] : [first.value, last.value])
const valid = computed(() => ['chapter', 'book'].includes(mode.value) || (bounds.value[0]! <= bounds.value[1]! && bounds.value.every(v => props.chapter.verses.some(s => s.number === v))))
const filtered = computed(() => ['book', 'chapter'].includes(mode.value) ? entries.value : entries.value.filter(e => overlaps(e, props.chapter.chapter.number, bounds.value[0]!, bounds.value[1]!)))
const identity = computed(() => `${props.chapter.translation.code}:${props.chapter.book.slug}:${props.chapter.chapter.number}:${mode.value === 'book'}:${selected.value.join(',')}`)
watch(selected, v => localStorage.setItem('bible-desktop:commentary-sources', JSON.stringify(v)), { deep: true })
watch(identity, () => { offset.value = 0; entries.value = []; expanded.value = new Set() })
watch([identity, offset, retry], async (_, __, cleanup) => {
  let stale = false; cleanup(() => { stale = true }); busy.value = true; error.value = false
  try {
    const available = await service.modules(); if (stale) return; modules.value = available
    if (!selected.value.length) { entries.value = []; total.value = 0; return }
    const canon = props.canon
    const osis = props.chapter.verses[0]?.osis_ref.split('.')[0]
    if (!canon || !osis) throw new Error('Canonical identity unavailable')
    const slug = await service.canonicalSlug(canon, osis)
    const page = await service.commentaries(slug, mode.value === 'book' ? null : props.chapter.chapter.number, selected.value, offset.value)
    if (stale) return
    entries.value = offset.value ? [...new Map([...entries.value, ...page.entries].map(e => [e.id, e])).values()] : page.entries
    total.value = page.total
  } catch { if (!stale) error.value = true } finally { if (!stale) busy.value = false }
}, { immediate: true })
function toggle(id: number) { const value = new Set(expanded.value); if (value.has(id)) value.delete(id); else value.add(id); expanded.value = value }
</script>
<template>
  <section class="commentaries">
    <h2>{{ text.commentaries }} · {{ chapter.book.name }} {{ chapter.chapter.number }}</h2>
    <details><summary>{{ text.sources }}</summary><p>{{ text.choose }}</p><input v-model="sourceQuery" type="search" :aria-label="text.sources" :placeholder="text.sources" /><div class="source-list"><label v-for="source in foundSources" :key="source.code" class="source"><input v-model="selected" type="checkbox" :value="source.code" :disabled="selected.length >= 30 && !selected.includes(source.code)" />{{ source.name }}</label></div></details>
    <label>{{ text.commentaries }}<select v-model="mode"><option v-for="value in (['book', 'chapter', 'verse', 'range', 'visible'] as const)" :key="value" :value="value">{{ text[value] }}</option></select></label>
    <div v-if="mode === 'range'" class="range"><label>{{ text.start }}<input v-model.number="first" type="number" min="1" /></label><label>{{ text.end }}<input v-model.number="last" type="number" min="1" /></label></div>
    <p v-if="!valid" role="alert">{{ text.invalid }}</p><p v-else-if="!['book','chapter'].includes(mode)">{{ chapter.book.name }} {{ chapter.chapter.number }}:{{ bounds[0] }}<template v-if="bounds[1] !== bounds[0]">–{{ bounds[1] }}</template></p>
    <p v-if="busy" role="status">{{ messages.loading }}</p><p v-if="error" role="alert">{{ text.error }} <button @click="retry++">{{ text.retry }}</button></p>
    <p v-if="!selected.length">{{ text.choose }}</p>
    <template v-if="valid">
      <article v-for="entry in filtered" :key="entry.id" class="commentary-entry">
        <h3>{{ entry.title || text.section }}</h3><p>{{ entry.author }} · {{ entry.module_name }}</p>
        <small v-if="entry.chapter_from">{{ chapter.book.name }} {{ entry.chapter_from }}<template v-if="entry.verse_from">:{{ entry.verse_from }}</template><template v-if="entry.chapter_to || entry.verse_to">–{{ entry.chapter_to ?? entry.chapter_from }}<template v-if="entry.verse_to">:{{ entry.verse_to }}</template></template></small>
        <PrayerContent v-if="expanded.has(entry.id)" :content="entry.body" /><p v-else>{{ prayerExcerpt(entry.body) }}…</p>
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
