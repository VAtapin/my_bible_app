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
import { studyContext } from '@/services/studyContext'
import { loadBibleCatalog } from '@/services/bibleCatalog'
import { enabledWebBibles, readWebChapter, synodalCode, webBibleBooks } from '@/services/webBibleLibrary'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { verseTarget } from '@/services/readerActions'
import { reactive } from 'vue'
import { ReaderNavigationHistory, type ReaderHistoryPlace } from '@/services/readerHistory'
import { useReaderPreferences, effectiveReaderPreferences } from '@/profile/readerPreferences'
import { readerControlMessages } from '@/i18n/readerControls'
import ReaderSettings from './ReaderSettings.vue'
import ReaderHistoryPanel from './ReaderHistoryPanel.vue'
import FavoriteTranslationSelect from './FavoriteTranslationSelect.vue'
import VerseNavigation from './VerseNavigation.vue'
import VerseStudyPanel from '@/features/study/VerseStudyPanel.vue'
import TemporaryPassage from '@/features/study/TemporaryPassage.vue'
import DictionaryContext from '@/features/study/DictionaryContext.vue'
import SourceCard from '@/features/study/SourceCard.vue'
import { createStudyApi } from '@/api/study'
import { createStudyService } from '@/services/studyService'
import { apiBaseUrl } from '@/config/api'
import { resolvedVerseChapter } from '@/services/verseLocations'
import type { ReferenceGroup } from '@/services/verseStudy'
import type { SavedPassage } from '@/services/personalStudy'
import ReaderToolbar from './ReaderToolbar.vue'
import { sourceInfoMessages } from '@/i18n/sourceInfo'

const chapterRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
const chapterService = createChapterService(bibleApi, chapterRepository)
const route = useRoute()
const { language, messages: text } = useI18n()
const appearance = useAppearance()
const readerPreferences = useReaderPreferences(); readerPreferences.initialize()
const display = computed(() => effectiveReaderPreferences(readerPreferences.preferences.value))
const controlText = computed(() => readerControlMessages[language.value])
const settingsOpen = ref(false), historyOpen = ref(false), versePickerOpen = ref(false), translationsOpen = ref(false)
const sourceOpen = ref(false)
const toolbarActions = computed(() => [
  {id:'compare',icon:'compare',label:comparing.value?text.value.parallel.close:text.value.parallel.open,pressed:comparing.value},
  {id:'bibles',icon:'bible',label:catalogText.value.catalog_add,to:'/bibles?tab=catalog'},
  {id:'search',icon:'search',label:text.value.readerActions.search,to:'/search'},
  {id:'commentary',icon:'commentary',label:studyMessages[language.value].commentaries,pressed:studying.value},
  {id:'settings',icon:'settings',label:controlText.value.settings},
  {id:'night',icon:display.value.night?'day':'night',label:display.value.night?controlText.value.day:controlText.value.night,pressed:display.value.night},
  {id:'source',icon:'info',label:sourceInfoMessages[language.value].title,pressed:sourceOpen.value},
  {id:'favorites',icon:'favorite',label:controlText.value.favorites},
  {id:'history',icon:'history',label:controlText.value.history},
  {id:'place',icon:'place',label:`${controlText.value.digital} / ${controlText.value.visual}`},
])
function toolbarAction(id:string) {
  switch(id){
    case 'compare': toggleComparison(); break
    case 'commentary': studySource.value=undefined;studyVerse.value=undefined;studying.value=!studying.value; break
    case 'settings': settingsOpen.value=true; break
    case 'night': readerPreferences.setPreferences({...readerPreferences.preferences.value,night:!display.value.night}); break
    case 'source': sourceOpen.value=!sourceOpen.value; break
    case 'favorites': translationsOpen.value=!translationsOpen.value; break
    case 'back': historyBack(); break
    case 'forward': historyForward(); break
    case 'history': if(comparing.value)comparisonView.value?.showHistory();else historyOpen.value=true; break
    case 'place': if(comparing.value)comparisonView.value?.choosePlace();else versePickerOpen.value=!versePickerOpen.value; break
  }
}
const history = reactive(new ReaderNavigationHistory(localStorage,'bible-desktop:reader-history:main'))
let historyRestoring = false, initialOpening = true
async function restoreHistory(place?: ReaderHistoryPlace) {
  if (!place) return
  historyRestoring=true
  try {translationCode.value=place.code;await loadBooks(place.book);chapterNumber.value=place.chapter;restoreOffset=place.offset;await openChapter(String(place.verse))} finally {historyRestoring=false}
}
function historyBack(){if(comparing.value)comparisonView.value?.historyBack();else void restoreHistory(history.back())}
function historyForward(){if(comparing.value)comparisonView.value?.historyForward();else void restoreHistory(history.forward())}
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
function changeFontSize(): void { readerPreferences.setPreferences({...readerPreferences.preferences.value,fontSize:display.value.fontSize>=23?17:display.value.fontSize+2}) }
watch(()=>display.value.fontSize,value=>fontSize.value=value,{immediate:true})

const comparisonCatalog = ref<TranslationSummary[]>([])
const comparing = ref(!route.query.book && localStorage.getItem('bible-desktop:compare-open')==='true')
watch(comparing,value=>localStorage.setItem('bible-desktop:compare-open',String(value)))
const comparisonView = ref<InstanceType<typeof ParallelReading>>()
const studying = ref(false)
const selection = ref<SavedPassage>()
const studyPanel = ref<InstanceType<typeof VerseStudyPanel>>()
const studySource = ref<BibleChapter>()
const studyVerse = ref<number>()
const temporary = ref<ReferenceGroup>()
const canonicalSlug = ref('')
const studyService = createStudyService(createStudyApi({baseUrl:apiBaseUrl}))
async function openStudy(source:BibleChapter,verse:BibleChapter['verses'][number]){studying.value=true;studySource.value=source;studyVerse.value=verse.number}
async function openStrong(number:string,source:BibleChapter,verse:BibleChapter['verses'][number]){studying.value=true;studySource.value=source;studyVerse.value=verse.number;await nextTick();studyPanel.value?.openStrong(number)}
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
const studyPosition = computed(()=>studyContext(studySource.value,studyVerse.value,visibleChapter.value,chapter.value,visibleFirst.value,visibleLast.value))
const contextualChapter = computed(()=>studyPosition.value.chapter)
const studyFirst = computed(()=>studyPosition.value.first), studyLast = computed(()=>studyPosition.value.last)
watch(contextualChapter,async(source,_,cleanup)=>{let stale=false;cleanup(()=>stale=true);canonicalSlug.value='';if(!source)return;try{const edition=translations.value.find(t=>t.code===source.translation.code);if(!edition?.canon_code)return;const slug=await studyService.canonicalSlug(edition.canon_code,source.verses[0]?.osis_ref.split('.')[0]??'');if(!stale)canonicalSlug.value=slug}catch{/* No canonical match is shown as unavailable rather than guessed. */}})
const openOffset = ref(0)
let restoreOffset = 0
let visibleOffset = 0
let saveChain: Promise<unknown> = Promise.resolve()
function selectVerse(source: BibleChapter, verse: BibleChapter['verses'][number]) { actionChapter.value = source; selectedVerse.value = verse.number }
function visiblePlace(source: BibleChapter, first: BibleChapter['verses'][number], last: BibleChapter['verses'][number], offset: number) {
  visibleChapter.value = source; visibleFirst.value = first.number; visibleLast.value = last.number
  visibleOffset = offset
  if(!comparing.value)history.observe({code:source.translation.code,book:source.book.slug,chapter:source.chapter.number,verse:first.number,offset})
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
    initialOpening = false
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
    const previous=visibleChapter.value??chapter.value
    if(!previous){await loadBooks();return}
    const previousOffset=visibleOffset, ref=previous.verses.find(v=>v.number===visibleFirst.value)?.osis_ref??previous.verses[0]?.osis_ref
    try {
      const nextBooks=await webBibleBooks(bibleApi,translationCode.value), target=nextBooks.find(b=>b.canonical_book?.osis_code===ref?.split('.')[0])
      if(!target)throw Error('Missing book')
      if(!ref)throw Error("Missing canonical reference")
      const candidate=await resolvedVerseChapter(translationCode.value,ref,chapterService)
      const verse=candidate.verses.find(v=>v.osis_ref===ref&&v.plain_text.trim())
      if(!verse)throw Error('Missing verse')
      books.value=nextBooks;bookSlug.value=target.slug;chapterNumber.value=candidate.chapter.number;restoreOffset=0;await openChapter(String(verse.number))
    } catch {translationCode.value=previous.translation.code;restoreOffset=previousOffset;message.value=controlText.value.invalid}
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
    if(!initialOpening&&!historyRestoring&&!comparing.value)history.navigate({code:value.translation.code,book:value.book.slug,chapter:value.chapter.number,verse:verseTarget(target,value.verses.map(v=>v.number))??value.verses[0]?.number??1,offset:openOffset.value})
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
async function moveBook(offset:number){const source=visibleChapter.value??chapter.value;if(!source)return;const index=books.value.findIndex(b=>b.slug===source.book.slug),book=books.value[index+offset];if(book){bookSlug.value=book.slug;chapterNumber.value=1;await openChapter('1')}}

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
  <MobileShell back-to="/today" :show-header="!chapter" :reading="!!chapter" :reading-viewport="!!chapter">
    <RouterLink v-if="!chapter" class="storage-link" to="/bibles?tab=catalog">{{ catalogText.catalog_add }}</RouterLink>
    <section v-if="!chapter" class="reader-heading">
      <span class="card-icon"><img src="/app-icons/library.png" alt="" /></span>
      <span><p class="eyebrow dark-eyebrow">{{ text.reader.eyebrow }}</p><h1>{{ text.reader.title }}</h1></span>
    </section>

    <p v-if="busy && !translations.length" class="status" role="status">{{ text.loading }}</p>
    <div v-else-if="!translations.length" class="status"><p>{{ message || catalogText.catalog_empty }}</p><RouterLink class="primary-action" to="/bibles?tab=catalog">{{ catalogText.catalog_add }}</RouterLink></div>
    <button v-if="comparing&amp;&amp;!chapter" class="primary-action" @click="comparisonView?.choosePlace()">{{ text.reader.chooseChapter }}</button>
    <details v-else-if="translations.length && selectedTranslation && (!chapter || pickerOpen)" class="chapter-card chapter-picker" :class="{'reader-picker-overlay':!!chapter}" :open="pickerOpen" @toggle="pickerOpen = ($event.currentTarget as HTMLDetailsElement).open">
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
    <article ref="readingElement" class="reading-card" :class="{'reader-night':display.night}" :style="{ '--reading-size': `${fontSize}px`, '--reader-line-height':String(display.lineHeight) }">
      <header class="reading-header">
        <RouterLink class="reader-home" to="/today" :aria-label="text.navigation.today" :title="text.navigation.today"><AppIcon name="home" /></RouterLink>
        <button type="button" :disabled="busy || (visibleChapter?.chapter.number ?? chapterNumber) <= 1" :aria-label="text.reader.previous" @click="moveChapter(-1)">←</button>
        <span class="reader-title" role="button" tabindex="0" :aria-label="text.reader.chooseChapter" @click="pickerOpen=!pickerOpen" @keydown.enter="pickerOpen=!pickerOpen"><p>{{ (visibleChapter ?? chapter).translation.name }}</p><h2>{{ (visibleChapter ?? chapter).book.name }}<small>{{ text.reader.chapterLabel }} {{ visibleChapter?.chapter.number ?? chapter.chapter.number }}<template v-if="visibleFirst">:{{ visibleFirst }}</template></small></h2></span>
        <button class="reader-chapter-button" type="button" :aria-label="text.reader.chooseChapter" :title="text.reader.chooseChapter" @click="comparing?comparisonView?.choosePlace():versePickerOpen=!versePickerOpen"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 3h18v18H3zM9 3v18M15 3v18M3 9h18M3 15h18" /></svg></button>
        <button v-if="appearance.theme.value !== 'warm'" class="reader-size-button" type="button" :aria-label="text.readerActions.size" @click="changeFontSize">Aa</button>
        <ReaderToolbar :label="controlText.settings" :close-label="controlText.close" :actions="toolbarActions" @action="toolbarAction" />
        <button type="button" :disabled="busy || (visibleChapter?.chapter.number ?? chapterNumber) >= (visibleChapter ?? chapter).book.chapters_count" :aria-label="text.reader.next" @click="moveChapter(1)">→</button>
      </header>
      <FavoriteTranslationSelect v-if="translationsOpen" :translations="translations" :selected="visibleChapter?.translation.code??translationCode" @select="code=>{if(comparing)comparisonView?.changeTranslation(code);else{translationCode=code;changeTranslation()}translationsOpen=false}" />
      <VerseNavigation v-if="versePickerOpen&&selectedBook&&!comparing" :code="translationCode" :book="selectedBook" :chapter="visibleChapter?.chapter.number??chapterNumber" :service="chapterService" @select="(number,verse)=>{chapterNumber=number;openChapter(String(verse));versePickerOpen=false}" @close="versePickerOpen=false" />
      <ReaderSettings v-if="settingsOpen" @close="settingsOpen=false" />
      <SourceCard v-if="sourceOpen&&(visibleChapter??chapter)" expanded :metadata="comparisonCatalog.find(item=>item.code===(comparisonView?.source()??visibleChapter??chapter)?.translation.code)??(comparisonView?.source()??visibleChapter??chapter)!.translation"/>
      <ReaderHistoryPanel v-if="historyOpen" :state="history.state" :can-back="history.canBack" :can-forward="history.canForward" @back="historyBack" @forward="historyForward" @select="index=>restoreHistory(history.select(index))" @close="historyOpen=false" />
      <ParallelReading v-if="comparing" ref="comparisonView" :primary="chapter" :primary-offset="openOffset" :catalog="comparisonCatalog" :service="chapterService" :selected-verse="selectedVerse" :bookmarks="bookmarkedVerseKeys" :selection="selection" @strong="openStrong" @study="openStudy" @visible="visiblePlace" @select="(number,source)=>{selectedVerse=number;actionChapter=source}" @bookmark="(source,verse)=>toggleBookmark(verse,source)" @actions="(source,verse,event)=>openVerseMenu(verse.number,event,source)" />
      <ContinuousReading v-else @chapter="moveChapter" @book="moveBook" :key="`${chapter.translation.code}:${chapter.book.slug}:${chapter.chapter.number}`" :initial="chapter" :service="chapterService" :initial-verse="selectedVerse" :initial-offset="openOffset" :selected-verse="selectedVerse" :selected-chapter="actionChapter?.chapter.number ?? chapter.chapter.number" :bookmarks="bookmarkedVerseKeys" :selection="selection" @strong="openStrong" @study="openStudy" @visible="visiblePlace" @select="selectVerse" @bookmark="(source, verse) => toggleBookmark(verse, source)" @actions="(source, verse, event) => openVerseMenu(verse.number, event, source)" />
    </article>
    <aside v-if="studying" class="study-pane"><button type="button" @click="studying = false;studySource=undefined;studyVerse=undefined">{{ studyMessages[language].close }}</button><CommentaryPanel v-if="contextualChapter" :chapter="contextualChapter" :canon="comparisonCatalog.find(item=>item.code===contextualChapter?.translation.code)?.canon_code" :verse="studyVerse" :visible-first="studyFirst" :visible-last="studyLast" /><VerseStudyPanel v-if="studySource??contextualChapter" ref="studyPanel" :chapter="(studySource??contextualChapter)!" :verse="studyVerse??visibleFirst" @open="temporary=$event"/><DictionaryContext v-if="contextualChapter" :book="canonicalSlug" :osis="contextualChapter.verses[0]?.osis_ref.split('.')[0]" :verse-numbers="contextualChapter.verses.filter(v=>v.number>=(studyFirst??1)&&v.number<=(studyLast??studyFirst??1)).map(v=>Number(v.osis_ref.split('.')[2]))" :verse-chapters="contextualChapter.verses.filter(v=>v.number>=(studyFirst??1)&&v.number<=(studyLast??studyFirst??1)).map(v=>Number(v.osis_ref.split('.')[1]))" :chapter="contextualChapter.chapter.number" :verse-ids="contextualChapter.verses.filter(v=>v.number>=(studyFirst??1)&&v.number<=(studyLast??studyFirst??1)).map(v=>v.id)"/></aside>
    </div>
    <VerseActions @study="openStudy" :service="chapterService" @selection="selection=$event" ref="actions" :chapter="actionChapter ?? visibleChapter ?? chapter" :selected-verse="selectedVerse" @message="message = $event" />
    <TemporaryPassage v-if="temporary" :group="temporary" :code="(studySource??contextualChapter)!.translation.code" :service="chapterService" :windows-available="!!comparisonView" @assign="(source,verse,id)=>{comparisonView?.preview(source,verse,id);temporary=undefined;studying=false}" @close="temporary=undefined"/>
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
.reader-study-layout{display:flex;flex:1;min-height:0;margin:0}
.reader-study-layout .reading-card{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0;width:100%;max-width:none;margin:0;padding:0;border:0;border-radius:0;box-shadow:none}
.reader-study-layout :deep(.continuous-scroll){flex:1;height:auto;min-height:0}
.reader-study-layout .reading-header{flex-shrink:0;padding:2px 0 4px;margin:0;gap:4px}.reader-title{cursor:pointer;min-width:0}.reader-title p{font-size:8px;margin:0}.reader-title h2{font-size:18px;line-height:1.15}.reader-title h2 small{font-size:12px;margin-top:1px}.reading-header button,.reader-home{min-width:28px;min-height:32px;height:32px;padding:4px;display:flex;align-items:center;justify-content:center}.reader-home svg,.reader-chapter-button svg{width:18px;height:18px}.reader-picker-overlay{position:fixed;z-index:40;top:52px;left:10px;right:10px;margin:0;max-height:calc(100dvh - 120px);overflow:auto}.reading-card{position:relative}.reading-card>:deep(.verse-navigation){position:absolute;z-index:35;top:52px;left:0;right:0;background:var(--white);border:1px solid var(--line);padding:10px;max-height:calc(100dvh - 120px);overflow:auto}
.reader-study-layout.studying { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr);grid-template-rows:minmax(0,1fr);gap:12px }
.study-pane { overflow:auto; min-height:0;min-width:0; margin:0 }
@media(max-width:700px) { .reader-study-layout.studying { grid-template-columns:minmax(0,1fr);grid-template-rows:minmax(0,1fr) minmax(0,1fr) } }
.reading-header:has(.reader-toolbar){grid-template-columns:28px 28px minmax(0,1fr) 32px 32px 36px 28px}
.reading-header:has(.reader-toolbar):not(:has(.reader-size-button)){grid-template-columns:28px 28px minmax(0,1fr) 32px 36px 28px}
.reader-night{--white:#17212d;--ink:#e2eaf4;--line:#415061;--light-blue:#2d4157;background:#101821;color:#e2eaf4}.reader-night :deep(.verse-text){color:#e2eaf4}.reader-night :deep(.chapter-heading){background:#17212d;color:#e2eaf4}
</style>
