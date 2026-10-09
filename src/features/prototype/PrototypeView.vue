<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { BibleBook, BibleChapter, TranslationSummary } from '@/api/contracts'
import { bibleApi } from '@/api'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { bookmarkKey, type Bookmark } from '@/offline/libraryRepository'
import { createChapterService } from '@/services/chapterService'
import { recordSanitizedError } from '@/diagnostics/productDiagnostics'
import { formatMessage, useI18n } from '@/i18n'
import { interfaceLocales } from '@/i18n/locale'
import { useAppearance } from '@/profile/appearance'
import AppIcon from '../../../azbuka-web/src/components/AppIcon.vue'
import VerseActions from './VerseActions.vue'
import ParallelReading from './ParallelReading.vue'
import ContinuousReading from './ContinuousReading.vue'
import CommentaryPanel from '@/features/study/CommentaryPanel.vue'
import { studyMessages } from '@/i18n/study'
import { loadBibleCatalog } from '@/services/bibleCatalog'
import { enabledWebBibles, readWebChapter, synodalCode, webBibleBooks } from '@/services/webBibleLibrary'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { verseTarget } from '@/services/readerActions'

const chapterRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
const chapterService = createChapterService(bibleApi, chapterRepository)
const route = useRoute()
const { language, messages: text } = useI18n()
const appearance = useAppearance()
const selectedVerse = ref<number>()
const fontSize = ref(19)
const pickerOpen = ref(true)
const actions = ref<InstanceType<typeof VerseActions>>()
const readingElement = ref<HTMLElement>()
async function openVerseMenu(number: number, event?: MouseEvent, source = chapter.value): Promise<void> {
  actionChapter.value = source
  selectedVerse.value = number
  const selection = window.getSelection()
  const target = event?.currentTarget as HTMLElement | undefined
  const snippet = target && selection?.anchorNode && selection.focusNode && target.contains(selection.anchorNode) && target.contains(selection.focusNode) ? selection.toString() : ''
  await nextTick()
  await actions.value?.open('menu', snippet)
}
function changeFontSize(): void { fontSize.value = fontSize.value >= 23 ? 17 : fontSize.value + 2 }

const comparisonCatalog = ref<TranslationSummary[]>([])
const comparing = ref(!route.query.book && localStorage.getItem('bible-desktop:compare-open')==='true')
watch(comparing,value=>localStorage.setItem('bible-desktop:compare-open',String(value)))
const comparisonView = ref<InstanceType<typeof ParallelReading>>()
const studying = ref(false)
const visibleFirst = ref<number>()
const visibleLast = ref<number>()
const translations = ref<TranslationSummary[]>([])
const books = ref<BibleBook[]>([])
const translationCode = ref('')
const bookSlug = ref('')
const chapterNumber = ref(1)
const chapter = ref<BibleChapter>()
const visibleChapter = ref<BibleChapter>()
const actionChapter = ref<BibleChapter>()
const openOffset = ref(0)
let restoreOffset = 0
let visibleOffset = 0
let saveChain: Promise<unknown> = Promise.resolve()
function selectVerse(source: BibleChapter, verse: BibleChapter['verses'][number]) { actionChapter.value = source; selectedVerse.value = verse.number }
function visiblePlace(source: BibleChapter, first: BibleChapter['verses'][number], last: BibleChapter['verses'][number], offset: number) {
  visibleChapter.value = source; visibleFirst.value = first.number; visibleLast.value = last.number
  visibleOffset = offset
  const location = { translationCode: source.translation.code, bookSlug: source.book.slug, chapter: source.chapter.number, verse: first.number, verseOffset: offset, updatedAt: new Date().toISOString() }
  saveChain = saveChain.catch(() => undefined).then(() => libraryRepository.saveReadingLocation(location))
}
async function toggleComparison() {
  const wasComparing = comparing.value
  if (visibleChapter.value) {
    const source = visibleChapter.value, offset = visibleOffset
    const target = visibleFirst.value
    if (comparing.value) comparing.value = false
    translationCode.value = source.translation.code
    await loadBooks(source.book.slug)
    chapterNumber.value = source.chapter.number
    restoreOffset = offset
    await openChapter(String(target ?? ''))
  }
  comparing.value = !wasComparing
}
const bookmarks = ref<Bookmark[]>([])
const message = ref('')
const busy = ref(false)

const selectedTranslation = computed(() => translations.value.find((item) => item.code === translationCode.value))
const catalogText = computed(() => bibleCatalogMessages[language.value])
const selectedBook = computed(() => books.value.find((item) => item.slug === bookSlug.value))
const bookmarkedVerseKeys = computed(() => new Set(bookmarks.value.map((item) => item.key)))

onMounted(async () => {
  busy.value = true
  message.value = text.value.reader.catalogLoading
  try {
    const [fullCatalog, savedLocation, savedBookmarks] = await Promise.all([
      enabledWebBibles(bibleApi),
      libraryRepository.getReadingLocation(),
      libraryRepository.listBookmarks(),
    ])
    translations.value = fullCatalog
    const requestedCode = typeof route.query.translation === 'string' ? route.query.translation : undefined
    if (requestedCode && !translations.value.some(item => item.code === requestedCode)) {
      const requested = (await loadBibleCatalog(bibleApi)).find(item => item.code === requestedCode)
      if (requested) translations.value.push(requested)
    }
    comparisonCatalog.value = translations.value
    const catalog = translations.value
    bookmarks.value = savedBookmarks
    translationCode.value = typeof route.query.translation === 'string' ? route.query.translation : catalog.find(item => item.code === savedLocation?.translationCode)?.code
      ?? catalog.find((item) => item.code === synodalCode)?.code
      ?? catalog[0]?.code
      ?? ''
    const restoredLocation = savedLocation?.translationCode === translationCode.value ? savedLocation : undefined
    const requestedBook = typeof route.query.book === 'string' ? route.query.book : restoredLocation?.bookSlug
    const requestedChapter = Number(route.query.chapter)
    await loadBooks(requestedBook)
    if (!books.value.length) { message.value = catalogText.value.catalog_empty; return }
    chapterNumber.value = Number.isInteger(requestedChapter) && requestedChapter > 0
      ? requestedChapter
      : restoredLocation?.chapter ?? 1
    const hasTarget = Boolean(requestedBook || restoredLocation)
    message.value = hasTarget ? text.value.reader.locationRestored : text.value.reader.chooseBook
    if (hasTarget) {
      const restoring = !route.query.book && restoredLocation
      restoreOffset = restoring ? restoredLocation.verseOffset ?? 0 : 0
      await openChapter(route.query.verse ?? (restoring && restoredLocation.verse ? String(restoredLocation.verse) : undefined))
    }
  } catch (error) {
    message.value = errorMessage(error)
  } finally {
    busy.value = false
  }
})

async function loadBooks(preferredBook?: string): Promise<void> {
  chapter.value = undefined
  books.value = []
  books.value = await webBibleBooks(bibleApi, translationCode.value)
  bookSlug.value = books.value.some((item) => item.slug === preferredBook)
    ? preferredBook!
    : books.value[0]?.slug ?? ''
  chapterNumber.value = 1
  chapter.value = undefined
}

async function changeTranslation(): Promise<void> {
  await run(async () => {
    await loadBooks()
    message.value = text.value.reader.booksUpdated
  })
}

function changeBook(): void {
  chapterNumber.value = 1
  chapter.value = undefined
}

async function openChapter(target?: unknown): Promise<void> {
  selectedVerse.value = undefined
  const book = selectedBook.value
  if (!book || chapterNumber.value < 1 || chapterNumber.value > book.chapters_count) {
    message.value = text.value.reader.invalidChapter
    return
  }

  await run(async () => {
    chapter.value = undefined
    visibleChapter.value = undefined; actionChapter.value = undefined
    openOffset.value = restoreOffset; restoreOffset = 0
    const value = await readWebChapter(chapterService, translationCode.value, bookSlug.value, chapterNumber.value)
    if (!value.verses.some(verse => verse.plain_text.trim())) { message.value = catalogText.value.catalog_local_missing; return }
    chapter.value = value
    message.value = ''
    pickerOpen.value = false
    selectedVerse.value = verseTarget(target, chapter.value.verses.map((item) => item.number))
    await nextTick()
    await libraryRepository.saveReadingLocation({
      translationCode: translationCode.value,
      bookSlug: bookSlug.value,
      chapter: chapterNumber.value,
      updatedAt: new Date().toISOString(),
    })
  })
}

watch(() => route.query, async (query) => {
  const requestedBook = query.book
  const requestedTranslation = query.translation
  if (typeof requestedBook !== 'string' || !translationCode.value) return
  await run(async () => {
    if (typeof requestedTranslation === 'string' && requestedTranslation !== translationCode.value) { translationCode.value = requestedTranslation; await loadBooks(requestedBook) }
    if (!books.value.some((book) => book.slug === requestedBook)) return
    bookSlug.value = requestedBook
    const number = Number(query.chapter)
    chapterNumber.value = Number.isInteger(number) && number > 0 ? number : 1
    await openChapter(query.verse)
  })
})

async function moveChapter(offset: number): Promise<void> {
  if (comparing.value) { await comparisonView.value?.move(offset); return }
  const book = selectedBook.value
  if (!book) return
  const next = (visibleChapter.value?.chapter.number ?? chapterNumber.value) + offset
  if (next < 1 || next > book.chapters_count) return
  chapterNumber.value = next
  await openChapter()
}

async function toggleBookmark(verse: BibleChapter['verses'][number], source = chapter.value): Promise<void> {
  if (!source) return
  const key = bookmarkKey(source.translation.code, source.book.slug, source.chapter.number, verse.number)
  const existing = bookmarks.value.find((item) => item.key === key)
  if (existing) {
    await libraryRepository.deleteBookmark(key)
    bookmarks.value = bookmarks.value.filter((item) => item.key !== key)
    message.value = formatMessage(text.value.reader.bookmarkRemoved, { verse: verse.number })
    return
  }

  const value: Bookmark = {
    key,
    translationCode: source.translation.code,
    translationName: source.translation.name,
    bookSlug: source.book.slug,
    bookName: source.book.name,
    chapter: source.chapter.number,
    verse: verse.number,
    text: verse.plain_text,
    createdAt: new Date().toISOString(),
  }
  await libraryRepository.putBookmark(value)
  bookmarks.value = [...bookmarks.value, value]
  message.value = formatMessage(text.value.reader.bookmarkAdded, { verse: verse.number })
}

async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true
  try {
    await action()
  } catch (error) {
    message.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : text.value.unknownError
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat(interfaceLocales[language.value], { dateStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <MobileShell back-to="/today">
    <RouterLink class="storage-link" to="/bibles?tab=catalog">{{ catalogText.catalog_add }}</RouterLink>
    <section v-if="!chapter" class="reader-heading">
      <span class="card-icon"><img src="/app-icons/library.png" alt="" /></span>
      <span><p class="eyebrow dark-eyebrow">{{ text.reader.eyebrow }}</p><h1>{{ text.reader.title }}</h1></span>
    </section>

    <p v-if="busy && !translations.length" class="status" role="status">{{ text.loading }}</p>
    <div v-else-if="!translations.length" class="status"><p>{{ message || catalogText.catalog_empty }}</p><RouterLink class="primary-action" to="/bibles?tab=catalog">{{ catalogText.catalog_add }}</RouterLink></div>
    <button v-if="comparing" class="primary-action" @click="comparisonView?.choosePlace()">{{ text.reader.chooseChapter }}</button>
    <details v-else-if="translations.length && selectedTranslation" class="chapter-card chapter-picker" :open="pickerOpen" @toggle="pickerOpen = ($event.currentTarget as HTMLDetailsElement).open">
      <summary>{{ text.reader.chooseChapter }} <span aria-hidden="true">⌄</span></summary>
      <h2 id="chapter-form-title" class="visually-hidden">{{ text.reader.chooseChapter }}</h2>
      <div class="fields">
        <label class="translation-field">
          <span>{{ text.translation }}</span>
          <select v-model="translationCode" :disabled="busy" @change="changeTranslation">
            <option v-for="translation in translations" :key="translation.code" :value="translation.code">{{ translation.name }}</option>
          </select>
        </label>
        <label>
          <span>{{ text.book }}</span>
          <select v-model="bookSlug" :disabled="busy" @change="changeBook">
            <option v-for="book in books" :key="book.slug" :value="book.slug">{{ book.name }}</option>
          </select>
        </label>
        <label>
          <span>{{ text.chapter }}</span>
          <input v-model.number="chapterNumber" type="number" min="1" :max="selectedBook?.chapters_count ?? 1" inputmode="numeric" :disabled="busy" />
        </label>
      </div>

      <button :disabled="busy || !selectedBook" class="primary-action" type="button" @click="openChapter">
        {{ busy ? text.loading : text.reader.openChapter }}
      </button>

    </details>
    <p v-if="message && translations.length && (!chapter || ![text.reader.chapterSaved, text.reader.locationRestored].includes(message))" class="status reader-status" role="status" aria-live="polite">{{ message }}</p>

    <div v-if="chapter" class="reader-study-layout" :class="{ studying }">
    <article ref="readingElement" class="reading-card" :style="{ '--reading-size': `${fontSize}px` }">
      <header class="reading-header">
        <button type="button" :disabled="busy || (visibleChapter?.chapter.number ?? chapterNumber) <= 1" :aria-label="text.reader.previous" @click="moveChapter(-1)">←</button>
        <span><p>{{ (visibleChapter ?? chapter).translation.name }}</p><h2>{{ (visibleChapter ?? chapter).book.name }}<small>{{ text.reader.chapterLabel }} {{ visibleChapter?.chapter.number ?? chapter.chapter.number }}<template v-if="visibleFirst">:{{ visibleFirst }}</template></small></h2></span>
        <button v-if="appearance.theme.value !== 'warm'" class="reader-size-button" type="button" :aria-label="text.readerActions.size" @click="changeFontSize">Aa</button>
        <button type="button" :disabled="busy || (visibleChapter?.chapter.number ?? chapterNumber) >= (visibleChapter ?? chapter).book.chapters_count" :aria-label="text.reader.next" @click="moveChapter(1)">→</button>
      </header>
      <button type="button" class="parallel-toggle" :aria-pressed="comparing" @click="toggleComparison">{{ comparing ? text.parallel.close : text.parallel.open }}</button>
      <button type="button" class="parallel-toggle" :aria-expanded="studying" @click="studying = !studying">{{ studyMessages[language].commentaries }}</button>
      <ParallelReading v-if="comparing" ref="comparisonView" :primary="chapter" :primary-offset="openOffset" :catalog="comparisonCatalog" :service="chapterService" :selected-verse="selectedVerse" :bookmarks="bookmarkedVerseKeys" @visible="visiblePlace" @select="(number,source)=>{selectedVerse=number;actionChapter=source}" @bookmark="(source,verse)=>toggleBookmark(verse,source)" @actions="(source,verse,event)=>openVerseMenu(verse.number,event,source)" />
      <ContinuousReading v-else :key="`${chapter.translation.code}:${chapter.book.slug}:${chapter.chapter.number}`" :initial="chapter" :service="chapterService" :initial-verse="selectedVerse" :initial-offset="openOffset" :selected-verse="selectedVerse" :selected-chapter="actionChapter?.chapter.number ?? chapter.chapter.number" :bookmarks="bookmarkedVerseKeys" @visible="visiblePlace" @select="selectVerse" @bookmark="(source, verse) => toggleBookmark(verse, source)" @actions="(source, verse, event) => openVerseMenu(verse.number, event, source)" />
    </article>
    <aside v-if="studying" class="study-pane"><button type="button" @click="studying = false">{{ studyMessages[language].close }}</button><CommentaryPanel :chapter="visibleChapter ?? chapter" :canon="comparisonCatalog.find(item=>item.code===(visibleChapter ?? chapter)?.translation.code)?.canon_code" :visible-first="visibleFirst" :visible-last="visibleLast" /></aside>
    </div>
    <VerseActions ref="actions" :chapter="actionChapter ?? visibleChapter ?? chapter" :selected-verse="selectedVerse" @message="message = $event" />
    <template #footer>
      <nav class="bottom-nav reader-nav" :aria-label="text.reader.title">
        <template v-if="appearance.theme.value === 'warm'">
          <button type="button" @click="changeFontSize"><AppIcon name="size" /><span>{{ text.readerActions.size }}</span></button>
          <RouterLink to="/more"><AppIcon name="theme" /><span>{{ text.readerActions.theme }}</span></RouterLink>
        </template>
        <template v-else>
          <RouterLink to="/storage?tab=bookmarks"><AppIcon name="bookmark" /><span>{{ text.storage.bookmarks }}</span></RouterLink>
          <RouterLink to="/storage?tab=notes"><AppIcon name="note" /><span>{{ text.storage.notes }}</span></RouterLink>
          <button v-if="appearance.theme.value === 'classic'" type="button" disabled :title="text.readerActions.audioUnavailable"><AppIcon name="audio" /><span>{{ text.readerActions.audio }}</span></button>
        </template>
        <button type="button" :disabled="!chapter" @click="actions?.open('share')"><AppIcon name="share" /><span>{{ text.readerActions.share }}</span></button>
        <RouterLink v-if="appearance.theme.value === 'warm'" to="/storage?tab=notes"><AppIcon name="note" /><span>{{ text.storage.notes }}</span></RouterLink>
        <button type="button" @click="actions?.open()"><AppIcon name="more" /><span>{{ text.navigation.more }}</span></button>
      </nav>
    </template>
  </MobileShell>
</template>

<style scoped>
.reader-study-layout.studying { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:12px }
.studying .reading-card { min-width:0; margin-top:0 }.study-pane { max-height:calc(100dvh - 220px); overflow:auto; min-width:0; margin-top:0 }
@media(max-width:700px) { .reader-study-layout.studying { grid-template-columns:minmax(0,1fr) } .studying :deep(.continuous-scroll) { height:35dvh } .study-pane { max-height:35dvh } }
.parallel-toggle { border: 1px solid var(--line); border-radius: 8px; padding: 8px 12px; background: var(--white, white); color: var(--ink); font: inherit; cursor: pointer; }
</style>
