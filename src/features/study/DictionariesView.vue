<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import PrayerContent from '@/components/PrayerContent.vue'
import AtlasImage from './AtlasImage.vue'
import { useI18n } from '@/i18n'
import { dictionaryMessages } from '@/i18n/dictionaries'
import { apiBaseUrl } from '@/config/api'
import { createDictionaryApi, dictionaryMediaUrl, type DictionaryModule, type DictionaryPage, type DictionaryArticle } from '@/api/dictionaries'
import { prayerExcerpt } from '@/services/prayerContent'
import { createDictionaryService } from '@/services/dictionaryService'
import TemporaryPassage from './TemporaryPassage.vue'
import type { ReferenceGroup } from '@/services/verseStudy'
import { createStudyApi } from '@/api/study'
import { createStudyService } from '@/services/studyService'
import { bibleApi } from '@/api'
import { loadBibleCatalog } from '@/services/bibleCatalog'
import { createChapterService } from '@/services/chapterService'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { resolveStudyReference } from '@/services/studyReference'
import SourceCard from './SourceCard.vue'
import DictionarySearch from './DictionarySearch.vue'
import { webBibleBooks, readWebChapter } from '@/services/webBibleLibrary'
const { language, messages } = useI18n(), text = computed(() => dictionaryMessages[language.value]), route = useRoute(), router = useRouter()
const service = createDictionaryService(createDictionaryApi({ baseUrl: apiBaseUrl })), modules = ref<DictionaryModule[]>([]), page = ref<DictionaryPage>(), article = ref<DictionaryArticle>(), forms = ref<{ module_code: string; standard_form: string }[]>([]), query = ref(''), busy = ref(false), failed = ref(false), retry = ref(0), kind = ref('all')
const reading = ref<HTMLElement>()
const readingKey = () => `dictionary:position:${code.value}:${source.value?.content_version}:${key.value}`
function savePosition() { if (reading.value) localStorage.setItem(readingKey(), String(reading.value.scrollTop)) }
const emit = defineEmits<{ reference: [reference: DictionaryArticle['references'][number]] }>()
const temporary = ref<ReferenceGroup>(), temporaryCode = ref('')
const chapterService = createChapterService(bibleApi, createIndexedDbChapterRepository()), studyService = createStudyService(createStudyApi({ baseUrl: apiBaseUrl }))
async function reference(value: DictionaryArticle['references'][number]) {
  emit('reference', value)
  try { const resolved = await resolveStudyReference(value,chapterService,source.value?.name??code.value);temporaryCode.value=resolved.code;temporary.value=resolved.group }
  catch { failed.value=true }
}
const code = computed(() => String(route.query.module ?? '')), key = computed(() => String(route.query.entry ?? '')), offset = computed(() => Math.max(0, Number(route.query.offset) || 0)), source = computed(() => modules.value.find(m => m.code === code.value)), available = computed(() => modules.value.filter(m => kind.value === 'all' || (kind.value === 'maps' ? m.media_count > 0 : m.word_forms_count > 0)))
watch([() => route.fullPath, retry], async (_, __, cleanup) => { let stale = false; cleanup(() => { stale = true }); busy.value = true; failed.value = false; page.value = undefined; article.value = undefined; forms.value=[]; try { const catalog = await service.modules(); const selected = catalog.find(m => m.code === code.value); const data = code.value && !key.value ? await service.entries(code.value, String(route.query.q ?? ''), offset.value) : undefined; const foundForms = code.value && !key.value && selected?.word_forms_count && String(route.query.q ?? '').trim() ? await service.lookup(String(route.query.q),[code.value]) : []; const body = code.value && key.value ? await service.article(code.value, key.value, selected?.content_version ?? null) : undefined; if (stale) return; modules.value = catalog; forms.value=foundForms; page.value = data; article.value = body; query.value = String(route.query.q ?? ''); if (body) { localStorage.setItem('bible-desktop:dictionary-resume', route.fullPath); await nextTick(); if (reading.value) reading.value.scrollTop = Number(localStorage.getItem(readingKey())) || 0 } } catch { if (!stale) failed.value = true } finally { if (!stale) busy.value = false } }, { immediate: true })
function open(module: string, entry = '', q = query.value) { void router.push({ path: '/dictionaries', query: { module, ...(entry ? { entry } : {}), ...(q ? { q } : {}) } }) }
async function search() { forms.value = []; if (source.value?.word_forms_count) { try { forms.value = await service.lookup(query.value, [code.value]) } catch { failed.value = true } } open(code.value, '', query.value) }
</script>
<template><MobileShell :back-to="key ? `/dictionaries?module=${encodeURIComponent(code)}` : code ? '/dictionaries' : '/more'"><h1>{{ text.title }}</h1><p v-if="busy" role="status">{{ messages.loading }}</p><p v-if="failed" role="alert">{{ text.error }} <button @click="retry++">{{ text.retry }}</button></p><template v-if="!code"><DictionarySearch :initial-query="String(route.query.q??'' )"/><select v-model="kind" :aria-label="text.sources"><option value="all">{{ text.all }}</option><option value="maps">{{ text.maps }}</option><option value="forms">{{ text.forms }}</option></select><button v-for="m in available" :key="m.code" class="module-card available" @click="open(m.code)"><strong>{{ prayerExcerpt(m.name) }}</strong><small>{{ m.language_code }} · {{ m.entries_count }}</small></button></template><template v-else><h2>{{ prayerExcerpt(source?.name??'') }}</h2><SourceCard v-if="source" :metadata="source" :version="source.content_version??undefined"/><template v-if="article"><h3>{{ prayerExcerpt(article.topic) }}</h3><div ref="reading" class="dictionary-reading" @scroll="savePosition"><PrayerContent :content="article.body"/></div><AtlasImage v-for="media in article.media" :key="media.id" :url="dictionaryMediaUrl(apiBaseUrl, code, media.id, media.url)" :title="article.topic" :version="source?.content_version" :module-code="code" :media-id="media.id"/><h3 v-if="article.links.length">{{ text.links }}</h3><button v-for="(link,index) in article.links" :key="`${link.key}:${index}`" @click="open(code, link.key)">{{ link.label || link.topic }}</button><h3 v-if="article.references.length">{{ text.references }}</h3><button v-for="r in article.references" :key="JSON.stringify(r)" @click="reference(r)">{{ r.book_slug }} {{ r.chapter_number ?? '' }}{{ r.verse_from ? `:${r.verse_from}${r.verse_to && r.verse_to !== r.verse_from ? `–${r.verse_to}` : ''}` : '' }}</button></template><template v-else><form @submit.prevent="search"><label>{{ text.search }}<input v-model="query" maxlength="120"></label><button>{{ text.search }}</button></form><button v-for="f in forms" :key="f.module_code + f.standard_form" @click="open(code, '', f.standard_form)">{{ f.standard_form }}</button><button v-for="entry in page?.data" :key="entry.key" class="module-card available" @click="open(code, entry.key)">{{ prayerExcerpt(entry.topic) }}</button><p v-if="page && !page.total">{{ text.empty }}</p><nav v-if="page"><button :disabled="!offset" @click="router.push({ query: { ...route.query, offset: Math.max(0, offset - 30) } })">{{ text.back }}</button><span>{{ offset + page.data.length }} / {{ page.total }}</span><button :disabled="offset + page.data.length >= page.total" @click="router.push({ query: { ...route.query, offset: offset + page.data.length } })">{{ text.more }}</button></nav></template></template><p>{{ text.offline }}</p><TemporaryPassage v-if="temporary" :group="temporary" :code="temporaryCode" :service="chapterService" @close="temporary=undefined"/></MobileShell></template>
<style scoped>.dictionary-reading{max-height:60dvh;overflow:auto;font-size:19px;line-height:1.65}nav{display:flex;justify-content:space-between;gap:10px;margin:16px 0}.module-card{width:100%;text-align:start;display:flex;justify-content:space-between;margin:8px 0}form{display:flex;flex-wrap:wrap;gap:8px}input{display:block;max-width:100%}</style>
