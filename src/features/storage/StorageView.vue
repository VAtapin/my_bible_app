<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MobileShell from '@/components/MobileShell.vue'
import type { StoredChapter } from '@/offline/chapterRepository'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import type { Bookmark, OfflinePackage } from '@/offline/libraryRepository'

const chaptersRepository = createIndexedDbChapterRepository()
const libraryRepository = createIndexedDbLibraryRepository()
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
  if (!window.confirm(`Удалить локальную копию «${chapter.data.book.name}, глава ${chapter.data.chapter.number}»?`)) return
  await chaptersRepository.delete(chapter.key)
  await libraryRepository.deletePackage(chapter.data.translation.code)
  message.value = 'Локальная глава удалена. Закладки и настройки сохранены.'
  await load()
}

async function clearContent(): Promise<void> {
  if (!chapters.value.length || !window.confirm('Удалить весь загруженный библейский текст? Закладки и профиль останутся.')) return
  busy.value = true
  try {
    await Promise.all(chapters.value.map((chapter) => chaptersRepository.delete(chapter.key)))
    await Promise.all(packages.value.map((item) => libraryRepository.deletePackage(item.translationCode)))
    message.value = 'Офлайн-тексты удалены. Закладки и профиль сохранены.'
    await load()
  } finally {
    busy.value = false
  }
}

async function removeBookmark(bookmark: Bookmark): Promise<void> {
  await libraryRepository.deleteBookmark(bookmark.key)
  bookmarks.value = bookmarks.value.filter((item) => item.key !== bookmark.key)
  message.value = 'Закладка удалена.'
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} Б`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} КБ`
  return `${(value / 1024 / 1024).toFixed(1)} МБ`
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium' }).format(new Date(value))
}
</script>

<template>
  <MobileShell back-to="/reader">
    <section class="simple-page storage-page">
      <p class="eyebrow dark-eyebrow">Без сети</p>
      <h1>Хранилище</h1>
      <p>Управляйте загруженными текстами отдельно от закладок и профиля.</p>

      <section class="storage-summary">
        <div><strong>{{ chapters.length }}</strong><small>глав сохранено</small></div>
        <div><strong>{{ formatBytes(approximateBytes) }}</strong><small>примерный объём</small></div>
        <div><strong>{{ bookmarks.length }}</strong><small>закладок</small></div>
      </section>
      <p v-if="message" class="status" role="status">{{ message }}</p>

      <section class="storage-section">
        <div class="section-heading-row">
          <span><small>Контент</small><h2>Офлайн-пакеты</h2></span>
          <button class="text-action danger-text" type="button" :disabled="busy || !chapters.length" @click="clearContent">Удалить всё</button>
        </div>
        <div v-if="packages.length" class="storage-list">
          <div v-for="item in packages" :key="item.translationCode" class="storage-item">
            <span><strong>{{ item.translationName }}</strong><small>{{ item.chapterCount }} глав · {{ formatBytes(item.approximateBytes) }} · {{ formatDate(item.downloadedAt) }}</small></span>
            <span class="complete-badge">Готов</span>
          </div>
        </div>
        <p v-else class="empty-state">Полные переводы ещё не загружены.</p>
      </section>

      <section class="storage-section">
        <div class="section-heading-row"><span><small>Главы</small><h2>Сохранено</h2></span></div>
        <div v-if="chapters.length" class="storage-list">
          <div v-for="item in chapters" :key="item.key" class="storage-item">
            <span><strong>{{ item.data.book.name }}, {{ item.data.chapter.number }}</strong><small>{{ item.data.translation.name }} · {{ formatDate(item.savedAt) }}</small></span>
            <button type="button" aria-label="Удалить главу" @click="removeChapter(item)">Удалить</button>
          </div>
        </div>
        <p v-else class="empty-state">Сохранённых глав пока нет.</p>
      </section>

      <section class="storage-section">
        <div class="section-heading-row"><span><small>Личное</small><h2>Закладки</h2></span></div>
        <div v-if="bookmarks.length" class="storage-list">
          <div v-for="item in bookmarks" :key="item.key" class="storage-item bookmark-item">
            <span><strong>{{ item.bookName }} {{ item.chapter }}:{{ item.verse }}</strong><small>{{ item.text }}</small></span>
            <button type="button" aria-label="Удалить закладку" @click="removeBookmark(item)">Удалить</button>
          </div>
        </div>
        <p v-else class="empty-state">Закладок пока нет.</p>
      </section>
    </section>
  </MobileShell>
</template>
