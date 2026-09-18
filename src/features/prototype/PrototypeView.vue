<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { BibleBook, BibleChapter, TranslationSummary } from '@/api/contracts'
import { bibleApi } from '@/api'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { bookmarkKey, type Bookmark } from '@/offline/libraryRepository'
import { createChapterService } from '@/services/chapterService'
import { createOfflinePackageService, type PackageProgress } from '@/services/offlinePackageService'
import { recordProductMetric, recordSanitizedError } from '@/diagnostics/productDiagnostics'

const chapterRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
const chapterService = createChapterService(bibleApi, chapterRepository)
const packageService = createOfflinePackageService(bibleApi, chapterRepository, libraryRepository)
const route = useRoute()

const translations = ref<TranslationSummary[]>([])
const books = ref<BibleBook[]>([])
const translationCode = ref('')
const bookSlug = ref('')
const chapterNumber = ref(1)
const chapter = ref<BibleChapter>()
const bookmarks = ref<Bookmark[]>([])
const message = ref('')
const busy = ref(false)
const packageBusy = ref(false)
const packageProgress = ref<PackageProgress>()
const packageVersion = ref('')
const packageUpdateAvailable = ref(false)
let packageAbortController: AbortController | undefined

const selectedTranslation = computed(() => translations.value.find((item) => item.code === translationCode.value))
const selectedBook = computed(() => books.value.find((item) => item.slug === bookSlug.value))
const bookmarkedVerseKeys = computed(() => new Set(bookmarks.value.map((item) => item.key)))

onMounted(async () => {
  busy.value = true
  message.value = 'Загружаем каталог…'
  try {
    const [catalog, savedLocation, savedBookmarks] = await Promise.all([
      bibleApi.getTranslations('ru'),
      libraryRepository.getReadingLocation(),
      libraryRepository.listBookmarks(),
    ])
    translations.value = catalog
    bookmarks.value = savedBookmarks
    translationCode.value = typeof route.query.translation === 'string' ? route.query.translation : savedLocation?.translationCode
      ?? catalog.find((item) => item.is_default)?.code
      ?? catalog[0]?.code
      ?? ''
    const requestedBook = typeof route.query.book === 'string' ? route.query.book : savedLocation?.bookSlug
    const requestedChapter = Number(route.query.chapter)
    await loadBooks(requestedBook)
    chapterNumber.value = Number.isInteger(requestedChapter) && requestedChapter > 0
      ? requestedChapter
      : savedLocation?.chapter ?? 1
    const hasTarget = Boolean(requestedBook || savedLocation)
    message.value = hasTarget ? 'Место чтения восстановлено.' : 'Выберите книгу и главу.'
    if (hasTarget) await openChapter()
  } catch (error) {
    message.value = errorMessage(error)
  } finally {
    busy.value = false
  }
})

async function loadBooks(preferredBook?: string): Promise<void> {
  if (!translationCode.value) return
  books.value = await bibleApi.getBooks(translationCode.value)
  bookSlug.value = books.value.some((item) => item.slug === preferredBook)
    ? preferredBook!
    : books.value[0]?.slug ?? ''
  chapterNumber.value = 1
  chapter.value = undefined
  const status = await packageService.inspect(translationCode.value)
  packageVersion.value = status.stored
    ? `${status.stored.chapterCount} глав · ${formatDate(status.stored.downloadedAt)}`
    : `Не загружен · ${status.totalChapters} глав`
  packageUpdateAvailable.value = status.updateAvailable
}

async function changeTranslation(): Promise<void> {
  await run(async () => {
    await loadBooks()
    message.value = 'Каталог книг обновлён.'
  })
}

function changeBook(): void {
  chapterNumber.value = 1
  chapter.value = undefined
}

async function openChapter(): Promise<void> {
  const book = selectedBook.value
  if (!book || chapterNumber.value < 1 || chapterNumber.value > book.chapters_count) {
    message.value = 'Выберите существующую главу.'
    return
  }

  await run(async () => {
    try {
      chapter.value = await chapterService.download(translationCode.value, bookSlug.value, chapterNumber.value)
      message.value = 'Глава открыта и сохранена для офлайна.'
    } catch (networkError) {
      chapter.value = await chapterService.readOffline(translationCode.value, bookSlug.value, chapterNumber.value)
      if (!chapter.value) throw networkError
      message.value = 'Нет сети — открыта сохранённая глава.'
    }
    await libraryRepository.saveReadingLocation({
      translationCode: translationCode.value,
      bookSlug: bookSlug.value,
      chapter: chapterNumber.value,
      updatedAt: new Date().toISOString(),
    })
  })
}

async function moveChapter(offset: number): Promise<void> {
  const book = selectedBook.value
  if (!book) return
  const next = chapterNumber.value + offset
  if (next < 1 || next > book.chapters_count) return
  chapterNumber.value = next
  await openChapter()
}

async function toggleBookmark(verse: BibleChapter['verses'][number]): Promise<void> {
  if (!chapter.value) return
  const key = bookmarkKey(translationCode.value, bookSlug.value, chapterNumber.value, verse.number)
  const existing = bookmarks.value.find((item) => item.key === key)
  if (existing) {
    await libraryRepository.deleteBookmark(key)
    bookmarks.value = bookmarks.value.filter((item) => item.key !== key)
    message.value = `Закладка на стих ${verse.number} удалена.`
    return
  }

  const value: Bookmark = {
    key,
    translationCode: translationCode.value,
    translationName: chapter.value.translation.name,
    bookSlug: bookSlug.value,
    bookName: chapter.value.book.name,
    chapter: chapterNumber.value,
    verse: verse.number,
    text: verse.plain_text,
    createdAt: new Date().toISOString(),
  }
  await libraryRepository.putBookmark(value)
  bookmarks.value = [...bookmarks.value, value]
  message.value = `Стих ${verse.number} добавлен в закладки.`
}

async function downloadTranslation(): Promise<void> {
  const translation = selectedTranslation.value
  if (!translation || !window.confirm(`Скачать перевод «${translation.name}» целиком? Загрузка может занять несколько минут.`)) return

  packageAbortController = new AbortController()
  packageBusy.value = true
  packageProgress.value = undefined
  message.value = 'Начинаем загрузку перевода…'
  try {
    const result = await packageService.download(
      translation,
      (progress) => { packageProgress.value = progress },
      packageAbortController.signal,
    )
    packageVersion.value = `${result.chapterCount} глав · ${formatDate(result.downloadedAt)}`
    packageUpdateAvailable.value = false
    recordProductMetric('offline_download_completed')
    message.value = 'Перевод полностью загружен и доступен без сети.'
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) recordSanitizedError('offline_download')
    message.value = error instanceof DOMException && error.name === 'AbortError'
      ? 'Загрузка остановлена. Готовая версия пакета не изменена.'
      : errorMessage(error)
  } finally {
    packageBusy.value = false
    packageAbortController = undefined
  }
}

function stopPackageDownload(): void {
  packageAbortController?.abort()
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
  return error instanceof Error ? error.message : 'Неизвестная ошибка.'
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <MobileShell>
    <section class="reader-heading">
      <span class="card-icon"><img src="/app-icons/library.png" alt="" /></span>
      <span><p class="eyebrow dark-eyebrow">Библия</p><h1>Чтение</h1></span>
      <RouterLink class="storage-link" to="/storage">Офлайн</RouterLink>
    </section>

    <section class="chapter-card" aria-labelledby="chapter-form-title">
      <h2 id="chapter-form-title" class="visually-hidden">Выбор главы</h2>
      <div class="fields">
        <label class="translation-field">
          <span>Перевод</span>
          <select v-model="translationCode" :disabled="busy || packageBusy" @change="changeTranslation">
            <option v-for="translation in translations" :key="translation.code" :value="translation.code">{{ translation.name }}</option>
          </select>
        </label>
        <label>
          <span>Книга</span>
          <select v-model="bookSlug" :disabled="busy || packageBusy" @change="changeBook">
            <option v-for="book in books" :key="book.slug" :value="book.slug">{{ book.name }}</option>
          </select>
        </label>
        <label>
          <span>Глава</span>
          <input v-model.number="chapterNumber" type="number" min="1" :max="selectedBook?.chapters_count ?? 1" inputmode="numeric" :disabled="busy || packageBusy" />
        </label>
      </div>

      <button :disabled="busy || packageBusy || !selectedBook" class="primary-action" type="button" @click="openChapter">
        {{ busy ? 'Загрузка…' : 'Открыть главу' }}
      </button>

      <div class="package-row">
        <div><strong>Офлайн-перевод</strong><small>{{ packageVersion }}<template v-if="packageUpdateAvailable"> · доступно обновление</template></small></div>
        <button v-if="!packageBusy" type="button" :disabled="busy" @click="downloadTranslation">{{ packageVersion.startsWith('Не загружен') ? 'Скачать' : 'Обновить' }}</button>
        <button v-else type="button" class="danger-text" @click="stopPackageDownload">Остановить</button>
      </div>
      <div v-if="packageProgress" class="download-progress" role="progressbar" :aria-valuenow="packageProgress.current" :aria-valuemax="packageProgress.total">
        <span :style="{ width: `${(packageProgress.current / packageProgress.total) * 100}%` }"></span>
        <small>{{ packageProgress.bookName }}, {{ packageProgress.chapter }} · {{ packageProgress.current }}/{{ packageProgress.total }}</small>
      </div>
      <p v-if="message" class="status" role="status" aria-live="polite">{{ message }}</p>
    </section>

    <article v-if="chapter" class="reading-card">
      <header class="reading-header">
        <button type="button" :disabled="busy || chapterNumber <= 1" aria-label="Предыдущая глава" @click="moveChapter(-1)">←</button>
        <span><p>{{ chapter.translation.name }}</p><h2>{{ chapter.book.name }}, глава {{ chapter.chapter.number }}</h2></span>
        <button type="button" :disabled="busy || chapterNumber >= chapter.book.chapters_count" aria-label="Следующая глава" @click="moveChapter(1)">→</button>
      </header>
      <ol>
        <li v-for="verse in chapter.verses" :key="verse.id">
          <button class="bookmark-button" :class="{ active: bookmarkedVerseKeys.has(bookmarkKey(translationCode, bookSlug, chapterNumber, verse.number)) }" type="button" :aria-label="`Закладка на стих ${verse.number}`" @click="toggleBookmark(verse)">
            {{ bookmarkedVerseKeys.has(bookmarkKey(translationCode, bookSlug, chapterNumber, verse.number)) ? '★' : '☆' }}
          </button>
          <span class="verse-number">{{ verse.number }}</span>{{ verse.plain_text }}
        </li>
      </ol>
    </article>
  </MobileShell>
</template>
