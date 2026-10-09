<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { searchLocalBible } from '@/services/bibleCatalog'
import { enabledWebBibles, isConnectionFailure } from '@/services/webBibleLibrary'
import { bibleApi } from '@/api'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import type { VerseSearchResult } from '@/api/contracts'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
const route = useRoute()
const router = useRouter()
const { language, messages: text } = useI18n()
const profile = useProfileStore()
const explicitTranslation = typeof route.query.translation === 'string'
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const translation = ref(typeof route.query.translation === 'string' ? route.query.translation : profile.load()?.bible.translationCodes[0] ?? '')
const results = ref<VerseSearchResult[]>([])
const busy = ref(false)
const message = ref('')
let generation = 0
async function search(): Promise<void> {
  const current = ++generation
  results.value = []
  if (query.value.trim().length < 2) { message.value = text.value.readerActions.searchHint; return }
  busy.value = true
  message.value = ''
  try {
    const chapters = createIndexedDbChapterRepository()
    const enabled = await enabledWebBibles(bibleApi)
    if (!explicitTranslation && !enabled.some(item => item.code === translation.value)) translation.value = enabled[0]?.code ?? ''
    if (!translation.value) { message.value = bibleCatalogMessages[language.value].catalog_empty; return }
    await router.replace({ path: '/search', query: { q: query.value.trim(), translation: translation.value } })
    let response: VerseSearchResult[]
    try { response = (await bibleApi.searchVerses(query.value.trim(), translation.value)).results }
    catch (error) {
      if (!isConnectionFailure(error)) throw error
      response = await searchLocalBible(chapters, translation.value, query.value.trim())
    }
    if (current !== generation) return
    results.value = response
    message.value = results.value.length ? '' : text.value.readerActions.noResults
  } catch { if (current === generation) message.value = text.value.readerActions.searchFailed }
  finally { if (current === generation) busy.value = false }
}
onMounted(() => { if (query.value.trim()) void search() })
onUnmounted(() => { generation++ })
</script>
<template>
  <MobileShell back-to="/reader">
    <h1 class="bible-search-title">{{ text.readerActions.search }}</h1>
    <form class="bible-search-form" @submit.prevent="search">
      <label for="bible-search" class="visually-hidden">{{ text.readerActions.search }}</label>
      <input id="bible-search" v-model="query" type="search" maxlength="500" :placeholder="text.readerActions.searchHint" />
      <button type="submit" :disabled="busy" class="primary-action">{{ busy ? text.loading : text.readerActions.find }}</button>
    </form>
    <p v-if="message" class="status" role="status">{{ message }}</p>
    <div class="content-catalog">
      <RouterLink v-for="item in results" :key="`${item.translation.code}-${item.verse_id}`" class="search-result" :to="{ path: '/reader', query: { translation: item.translation.code, book: item.book.slug, chapter: String(item.chapter_number), verse: String(item.verse_number) } }"><strong>{{ item.reference }}</strong><p>{{ item.snippet }}</p></RouterLink>
    </div>
  </MobileShell>
</template>
