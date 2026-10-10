<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import PrayerContent from '@/components/PrayerContent.vue'
import CommentaryAnnotations from './CommentaryAnnotations.vue'
import { useI18n } from '@/i18n'
import { studyMessages } from '@/i18n/study'
import { createStudyApi, type BookContents, type StudyArticle } from '@/api/study'
import { apiBaseUrl } from '@/config/api'
import { createStudyService } from '@/services/studyService'
const { language, messages } = useI18n(), route = useRoute(), router = useRouter()
const text = computed(() => studyMessages[language.value]), service = createStudyService(createStudyApi({ baseUrl: apiBaseUrl }))
const contents = ref<BookContents>(), article = ref<StudyArticle>(), busy = ref(false), error = ref(false), retry = ref(0), reading = ref<HTMLElement>()
const sourceNames = ref(new Map<string, string>())
void service.modules().then(sources => { sourceNames.value = new Map(sources.map(s => [s.code, s.name])) }).catch(() => undefined)
const bookId = computed(() => Number(route.params.id)), sectionId = computed(() => Number(route.query.section) || 0), offset = computed(() => Math.max(0, Number(route.query.offset) || 0))
const index = computed(() => contents.value?.sections.findIndex(s => s.id === sectionId.value) ?? -1)
const positionKey = () => `bible-desktop:study-position:${bookId.value}:${sectionId.value}`
function savePosition() { localStorage.setItem(positionKey(), String(reading.value?.scrollTop ?? 0)) }
watch([() => route.fullPath, retry], async (_, __, cleanup) => {
  let stale = false; cleanup(() => { stale = true }); busy.value = true; error.value = false; article.value = undefined; contents.value = undefined
  try {
    const data = await service.contents(bookId.value, offset.value)
    const body = sectionId.value ? await service.article(bookId.value, sectionId.value) : undefined
    if (stale) return
    contents.value = data; article.value = body
    if (body) { localStorage.setItem('bible-desktop:study-resume', route.fullPath); await nextTick(); if (reading.value) reading.value.scrollTop = Number(localStorage.getItem(positionKey())) || 0 }
  } catch { if (!stale) error.value = true } finally { if (!stale) busy.value = false }
}, { immediate: true })
async function move(delta: number) {
  if (!contents.value || index.value < 0) return
  let targetOffset = offset.value, sections = contents.value.sections, target = index.value + delta
  if (target < 0 || target >= sections.length) {
    targetOffset = delta < 0 ? Math.max(0, targetOffset - 20) : targetOffset + sections.length
    busy.value = true
    try { sections = (await service.contents(bookId.value, targetOffset)).sections; target = delta < 0 ? sections.length - 1 : 0 }
    catch { error.value = true; return } finally { busy.value = false }
  }
  const section = sections[target]; if (section) void router.push({ query: { section: section.id, offset: targetOffset } })
}
</script>
<template>
  <MobileShell :back-to="sectionId ? `/books/${bookId}?offset=${offset}` : '/books'" :reading="!!article" :reading-viewport="!!article">
    <p v-if="busy" role="status">{{ messages.loading }}</p>
    <p v-if="error" role="alert">{{ text.error }} <button @click="retry++">{{ text.retry }}</button></p>
    <template v-if="contents">
      <h1 class="compact-page-title">{{ contents.book.title }}</h1><p>{{ [contents.book.author, sourceNames.get(contents.book.module_code)].filter(Boolean).join(' · ') }}</p>
      <p v-if="contents.book.description">{{ contents.book.description }}</p>
      <template v-if="article">
        <h2>{{ article.title || text.section }}</h2><p v-if="article.author">{{ article.author }}</p>
        <div ref="reading" class="study-reading" @scroll="savePosition"><PrayerContent :content="article.body" /><CommentaryAnnotations :annotations="article.annotations" :module-code="contents.book.module_code" :source="sourceNames.get(contents.book.module_code)??contents.book.module_code"/></div>
        <nav><button :disabled="busy || offset + index <= 0 || index < 0" @click="move(-1)">{{ text.previous }}</button><RouterLink :to="`/books/${bookId}?offset=${offset}`">{{ text.contents }}</RouterLink><button :disabled="busy || index < 0 || offset + index + 1 >= contents.total" @click="move(1)">{{ text.next }}</button></nav>
      </template>
      <template v-else>
        <h2>{{ text.contents }} · {{ contents.total }}</h2>
        <div class="module-list"><RouterLink v-for="section in contents.sections" :key="section.id" class="module-card available" :to="{ query: { section: section.id, offset } }"><span><strong>{{ section.title || `${text.section} ${section.id}` }}</strong><small>{{ section.author }}</small></span></RouterLink></div>
        <nav><button :disabled="offset === 0" @click="router.push({ query: { offset: Math.max(0, offset - 20) } })">{{ text.previous }}</button><button :disabled="offset + contents.sections.length >= contents.total" @click="router.push({ query: { offset: offset + contents.sections.length } })">{{ text.next }}</button></nav>
      </template>
    </template>
    <p class="status">{{ text.offline }}</p>
  </MobileShell>
</template>
<style scoped>.study-reading { flex:1;min-height:0;overflow:auto; font-size:19px; line-height:1.65; white-space:pre-wrap } nav { display:flex; justify-content:space-between; gap:12px; margin:8px 0 } h2 { font-size:20px }</style>
