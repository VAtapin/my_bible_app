<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MobileShell from '@/components/MobileShell.vue'
import type { StoredChapter } from '@/offline/chapterRepository'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import type { Bookmark, OfflinePackage } from '@/offline/libraryRepository'
import { formatMessage, useI18n } from '@/i18n'

const chaptersRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
const { language, messages: text } = useI18n()
const chapters = ref<StoredChapter[]>([])
const packages = ref<OfflinePackage[]>([])
const bookmarks = ref<Bookmark[]>([])
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
    [chapters.value, packages.value, bookmarks.value] = await Promise.all([
      chaptersRepository.list(),
      libraryRepository.listPackages(),
      libraryRepository.listBookmarks(),
    ])
  } finally {
    busy.value = false
  }
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
  return new Intl.DateTimeFormat(language.value === 'de' ? 'de-DE' : 'ru-RU', { dateStyle: 'medium' }).format(new Date(value))
}
</script>

<template>
  <MobileShell back-to="/reader">
    <section class="simple-page storage-page">
      <p class="eyebrow dark-eyebrow">{{ text.storage.eyebrow }}</p>
      <h1>{{ text.storage.title }}</h1>
      <p>{{ text.storage.intro }}</p>

      <section class="storage-summary">
        <div><strong>{{ chapters.length }}</strong><small>{{ text.storage.savedChapters }}</small></div>
        <div><strong>{{ formatBytes(approximateBytes) }}</strong><small>{{ text.storage.approximateSize }}</small></div>
        <div><strong>{{ bookmarks.length }}</strong><small>{{ text.storage.bookmarks }}</small></div>
      </section>
      <p v-if="message" class="status" role="status">{{ message }}</p>

      <section class="storage-section">
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

      <section class="storage-section">
        <div class="section-heading-row"><span><small>{{ text.storage.chapters }}</small><h2>{{ text.storage.saved }}</h2></span></div>
        <div v-if="chapters.length" class="storage-list">
          <div v-for="item in chapters" :key="item.key" class="storage-item">
            <span><strong>{{ item.data.book.name }}, {{ item.data.chapter.number }}</strong><small>{{ item.data.translation.name }} · {{ formatDate(item.savedAt) }}</small></span>
            <button type="button" :aria-label="text.storage.deleteChapter" @click="removeChapter(item)">{{ text.storage.delete }}</button>
          </div>
        </div>
        <p v-else class="empty-state">{{ text.storage.noChapters }}</p>
      </section>

      <section class="storage-section">
        <div class="section-heading-row"><span><small>{{ text.storage.personal }}</small><h2>{{ text.storage.bookmarks }}</h2></span></div>
        <div v-if="bookmarks.length" class="storage-list">
          <div v-for="item in bookmarks" :key="item.key" class="storage-item bookmark-item">
            <span><strong>{{ item.bookName }} {{ item.chapter }}:{{ item.verse }}</strong><small>{{ item.text }}</small></span>
            <button type="button" :aria-label="text.storage.delete" @click="removeBookmark(item)">{{ text.storage.delete }}</button>
          </div>
        </div>
        <p v-else class="empty-state">{{ text.storage.noBookmarks }}</p>
      </section>
    </section>
  </MobileShell>
</template>
