<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import type { StoredChapter } from '@/offline/chapterRepository'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import type { Bookmark, OfflinePackage } from '@/offline/libraryRepository'
import { parseBookmarkKey } from '@/offline/libraryRepository'
import { createVerseNoteRepository, type VerseNote } from '@/offline/verseNotes'
import { savedVerseLink } from '@/services/readerActions'
import { formatMessage, useI18n } from '@/i18n'
import { interfaceLocales } from '@/i18n/locale'
import PersonalStudyLibrary from './PersonalStudyLibrary.vue'
import { personalStudyMessages } from '@/i18n/personalStudy'

const chaptersRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
const notesRepository = createVerseNoteRepository()
const route = useRoute()
const { language, messages: text } = useI18n()
const chapters = ref<StoredChapter[]>([])
const packages = ref<OfflinePackage[]>([])
const bookmarks = ref<Bookmark[]>([])
const notes = ref<VerseNote[]>([])
const tab = computed(() => route.query.tab === 'study' ? 'study' : route.query.tab === 'notes' ? 'notes' : route.query.tab === 'bookmarks' ? 'bookmarks' : 'all')
const title = computed(() => tab.value === 'notes' ? text.value.readerActions.myNotes : tab.value === 'bookmarks' ? text.value.readerActions.myBookmarks : text.value.storage.title)
const sortedBookmarks = computed(() => [...bookmarks.value].sort((a, b) => b.createdAt.localeCompare(a.createdAt)))
const noteEntries = computed(() => notes.value.map((note) => {
  const location = parseBookmarkKey(note.key)
  const saved = chapters.value.find((item) => item.data.translation.code === location?.translationCode && item.data.book.slug === location?.bookSlug && item.data.chapter.number === location?.chapter)?.data
  const bookmark = bookmarks.value.find((item) => item.key === note.key)
  return { note, location, bookName: note.location?.bookName ?? saved?.book.name ?? bookmark?.bookName ?? location?.bookSlug,
    translationName: note.location?.translationName ?? saved?.translation.name ?? bookmark?.translationName ?? location?.translationCode }
}))
const editingKey = ref('')
const draft = ref('')
const loaded = ref(false)
const message = ref('')
const busy = ref(false)
const approximateBytes = computed(() => chapters.value.reduce(
  (total, chapter) => total + new Blob([JSON.stringify(chapter.data)]).size,
  0,
))

onMounted(load)

async function load(): Promise<void> {
  busy.value = true
  try {
    [chapters.value, packages.value, bookmarks.value, notes.value] = await Promise.all([
      chaptersRepository.list(),
      libraryRepository.listPackages(),
      libraryRepository.listBookmarks(),
      notesRepository.list(),
    ])
    loaded.value = true
  } catch {
    message.value = text.value.storage.loadFailed
  } finally {
    busy.value = false
  }
}

function editNote(note: VerseNote): void {
  editingKey.value = note.key
  draft.value = note.text
}

async function saveNote(note: VerseNote): Promise<void> {
  if (!draft.value.trim() || busy.value) return
  busy.value = true
  try {
    await notesRepository.save({ ...note, text: draft.value.trim(), updatedAt: new Date().toISOString() })
    notes.value = await notesRepository.list()
    editingKey.value = ''
    message.value = text.value.readerActions.noteSaved
  } catch { message.value = text.value.readerActions.noteFailed }
  finally { busy.value = false }
}

async function removeChapter(chapter: StoredChapter): Promise<void> {
  if (!window.confirm(formatMessage(text.value.storage.removeChapterConfirm, { name: chapter.data.book.name, chapter: chapter.data.chapter.number }))) return
  await chaptersRepository.delete(chapter.key)
  await libraryRepository.deletePackage(chapter.data.translation.code)
  message.value = text.value.storage.chapterRemoved
  await load()
}

async function clearContent(): Promise<void> {
  if (!chapters.value.length || !window.confirm(text.value.storage.clearConfirm)) return
  busy.value = true
  try {
    await Promise.all(chapters.value.map((chapter) => chaptersRepository.delete(chapter.key)))
    await Promise.all(packages.value.map((item) => libraryRepository.deletePackage(item.translationCode)))
    message.value = text.value.storage.contentRemoved
    await load()
  } finally {
    busy.value = false
  }
}

async function removeBookmark(bookmark: Bookmark): Promise<void> {
  await libraryRepository.deleteBookmark(bookmark.key)
  bookmarks.value = bookmarks.value.filter((item) => item.key !== bookmark.key)
  message.value = text.value.storage.bookmarkRemoved
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat(interfaceLocales[language.value], { dateStyle: 'medium' }).format(new Date(value))
}
</script>

<template>
  <MobileShell back-to="/reader">
    <section class="simple-page storage-page">
      <p v-if="tab === 'all'" class="eyebrow dark-eyebrow">{{ text.storage.eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p class="reader-status">{{ tab === 'all' ? text.storage.intro : text.storage.personalLocal }}</p>
      <nav class="saved-tabs" :aria-label="text.storage.personal">
        <RouterLink to="/storage?tab=study" :class="{active:tab==='study'}">{{ personalStudyMessages[language].library }}</RouterLink>
        <RouterLink to="/storage?tab=bookmarks" :class="{ active: tab === 'bookmarks' }">{{ text.storage.bookmarks }} · {{ bookmarks.length }}</RouterLink>
        <RouterLink to="/storage?tab=notes" :class="{ active: tab === 'notes' }">{{ text.storage.notes }} · {{ notes.length }}</RouterLink>
        <RouterLink to="/storage" :class="{ active: tab === 'all' }">{{ text.reader.offline }}</RouterLink>
      </nav>

      <section v-if="tab === 'all'" class="storage-summary">
        <div><strong>{{ chapters.length }}</strong><small>{{ text.storage.savedChapters }}</small></div>
        <div><strong>{{ formatBytes(approximateBytes) }}</strong><small>{{ text.storage.approximateSize }}</small></div>
        <div><strong>{{ bookmarks.length }}</strong><small>{{ text.storage.bookmarks }}</small></div>
      </section>
      <p v-if="message" class="status" role="status">{{ message }}</p>
      <button v-if="!loaded && !busy" class="text-action" type="button" @click="load">{{ text.storage.retry }}</button>

      <section v-if="tab === 'all' && loaded" class="storage-section">
        <div class="section-heading-row">
          <span><small>{{ text.storage.content }}</small><h2>{{ text.storage.packages }}</h2></span>
          <button class="text-action danger-text" type="button" :disabled="busy || !chapters.length" @click="clearContent">{{ text.storage.deleteAll }}</button>
        </div>
        <div v-if="packages.length" class="storage-list">
          <div v-for="item in packages" :key="item.translationCode" class="storage-item">
            <span><strong>{{ item.translationName }}</strong><small>{{ item.chapterCount }} {{ text.reader.chapters }} · {{ formatBytes(item.approximateBytes) }} · {{ formatDate(item.downloadedAt) }}</small></span>
            <span class="complete-badge">{{ text.storage.ready }}</span>
          </div>
        </div>
        <p v-else class="empty-state">{{ text.storage.noPackages }}</p>
      </section>

      <section v-if="tab === 'all' && loaded" class="storage-section">
        <div class="section-heading-row"><span><small>{{ text.storage.chapters }}</small><h2>{{ text.storage.saved }}</h2></span></div>
        <div v-if="chapters.length" class="storage-list">
          <div v-for="item in chapters" :key="item.key" class="storage-item">
            <span><strong>{{ item.data.book.name }}, {{ item.data.chapter.number }}</strong><small>{{ item.data.translation.name }} · {{ formatDate(item.savedAt) }}</small></span>
            <button type="button" :aria-label="text.storage.deleteChapter" @click="removeChapter(item)">{{ text.storage.delete }}</button>
          </div>
        </div>
        <p v-else class="empty-state">{{ text.storage.noChapters }}</p>
      </section>

      <PersonalStudyLibrary v-if="tab === 'study'" />
      <section v-if="tab !== 'notes' && tab !== 'study' && loaded" class="storage-section">
        <div v-if="tab === 'all'" class="section-heading-row"><span><small>{{ text.storage.personal }}</small><h2>{{ text.storage.bookmarks }}</h2></span></div>
        <div v-if="bookmarks.length" class="storage-list">
          <div v-for="item in sortedBookmarks" :key="item.key" class="storage-item bookmark-item">
            <RouterLink :to="savedVerseLink(item)" class="saved-entry-link"><strong>{{ item.bookName }} {{ item.chapter }}:{{ item.verse }}</strong><small>{{ item.translationName }}</small><p>{{ item.text }}</p></RouterLink>
            <button type="button" :aria-label="text.storage.delete" @click="removeBookmark(item)">{{ text.storage.delete }}</button>
          </div>
        </div>
        <p v-else class="empty-state">{{ text.storage.noBookmarks }}</p>
      </section>
      <section v-if="tab !== 'bookmarks' && tab !== 'study' && loaded" class="storage-section">
        <div v-if="tab === 'all'" class="section-heading-row"><h2>{{ text.readerActions.myNotes }}</h2></div>
        <div v-if="noteEntries.length" class="storage-list">
          <article v-for="entry in noteEntries" :key="entry.note.key" class="saved-note">
            <RouterLink v-if="entry.location" :to="savedVerseLink(entry.location)" class="saved-entry-link"><strong>{{ entry.bookName }} {{ entry.location.chapter }}:{{ entry.location.verse }} →</strong><small>{{ entry.translationName }}</small></RouterLink>
            <strong v-else>{{ entry.note.key }}</strong>
            <form v-if="editingKey === entry.note.key" @submit.prevent="saveNote(entry.note)">
              <label class="visually-hidden" :for="`edit-${entry.note.key}`">{{ text.storage.editNote }}</label>
              <textarea :id="`edit-${entry.note.key}`" v-model="draft" rows="4" maxlength="10000" :disabled="busy" />
              <button type="submit" :disabled="busy || !draft.trim()">{{ text.readerActions.saveNote }}</button>
              <button type="button" :disabled="busy" @click="editingKey = ''">{{ text.storage.cancelEdit }}</button>
            </form>
            <template v-else><p>{{ entry.note.text }}</p><button class="text-action" type="button" @click="editNote(entry.note)">{{ text.storage.editNote }}</button></template>
          </article>
        </div>
        <p v-else class="empty-state">{{ text.storage.noNotes }}</p>
      </section>
    </section>
  </MobileShell>
</template>
