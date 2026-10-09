<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { studyMessages } from '@/i18n/study'
import { createStudyApi, type StudyBook } from '@/api/study'
import { apiBaseUrl } from '@/config/api'
import { createStudyService } from '@/services/studyService'
const { language, messages } = useI18n()
const text = computed(() => studyMessages[language.value])
const route = useRoute(), router = useRouter()
const service = createStudyService(createStudyApi({ baseUrl: apiBaseUrl }))
const query = ref(String(route.query.q ?? '')), books = ref<StudyBook[]>([]), total = ref(0), busy = ref(false), error = ref(false), retry = ref(0)
const offset = computed(() => Math.max(0, Number(route.query.offset) || 0))
const resume = ref(localStorage.getItem('bible-desktop:study-resume') ?? '')
const sourceNames = ref(new Map<string, string>())
void service.modules().then(sources => { sourceNames.value = new Map(sources.map(s => [s.code, s.name])) }).catch(() => undefined)
watch([() => route.query, retry], async (_, __, cleanup) => {
  let stale = false; cleanup(() => { stale = true }); busy.value = true; error.value = false; books.value = []
  try { const page = await service.books(String(route.query.q ?? ''), offset.value); if (!stale) { books.value = page.data; total.value = page.total } }
  catch { if (!stale) error.value = true } finally { if (!stale) busy.value = false }
}, { immediate: true })
function search() { void router.replace({ path: '/books', query: { q: query.value.trim() } }) }
</script>
<template>
  <MobileShell back-to="/more">
    <h1 class="compact-page-title">{{ text.books }}</h1>
    <button type="button" :disabled="busy" @click="retry++">{{ text.retry }}</button>
    <RouterLink v-if="/^\/books\/\d+\?/.test(resume)" :to="resume">{{ text.resume }}</RouterLink>
    <form class="study-search" @submit.prevent="search"><label for="study-search">{{ text.search }}</label><input id="study-search" v-model="query" maxlength="100" type="search" /><button type="submit">{{ text.search }}</button></form>
    <p v-if="busy" role="status">{{ messages.loading }}</p>
    <p v-else-if="error" role="alert">{{ text.error }} <button @click="retry++">{{ text.retry }}</button></p>
    <template v-else>
      <p>{{ text.count }}: {{ total }}</p><p v-if="!books.length">{{ text.empty }}</p>
      <div class="module-list"><RouterLink v-for="book in books" :key="book.id" class="module-card available" :to="`/books/${book.id}`"><span><strong>{{ book.title }}</strong><small>{{ [book.author, sourceNames.get(book.module_code)].filter(Boolean).join(' · ') }}</small></span></RouterLink></div>
      <nav class="study-pages"><button :disabled="offset === 0" @click="router.push({ path: '/books', query: { q: query, offset: Math.max(0, offset - 20) } })">{{ text.previous }}</button><button :disabled="offset + books.length >= total" @click="router.push({ path: '/books', query: { q: query, offset: offset + books.length } })">{{ text.next }}</button></nav>
    </template>
    <p class="status">{{ text.offline }}</p>
  </MobileShell>
</template>
<style scoped>.study-search { display:grid; gap:8px; margin:16px 0 }.study-pages { display:flex; justify-content:space-between; gap:12px; margin:16px 0 }</style>
