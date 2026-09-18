<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import type { BibleChapter } from '@/api/contracts'
import { bibleApi } from '@/api'
import { ru } from '@/i18n/ru'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createChapterService } from '@/services/chapterService'
import { schedulePrototypeNotification } from '@/notifications/localNotifications'

const service = createChapterService(bibleApi, createIndexedDbChapterRepository())

const translationCode = ref('BQ_RUSSIAN_RST_STRONG')
const bookSlug = ref('genesis')
const chapterNumber = ref(1)
const chapter = ref<BibleChapter>()
const message = ref('')
const busy = ref(false)
const isOnline = ref(navigator.onLine)

function updateConnectionStatus(): void {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  window.addEventListener('online', updateConnectionStatus)
  window.addEventListener('offline', updateConnectionStatus)
})

onUnmounted(() => {
  window.removeEventListener('online', updateConnectionStatus)
  window.removeEventListener('offline', updateConnectionStatus)
})

async function download(): Promise<void> {
  await run(async () => {
    chapter.value = await service.download(translationCode.value, bookSlug.value, chapterNumber.value)
    message.value = ru.saved
  })
}

async function openOffline(): Promise<void> {
  await run(async () => {
    chapter.value = await service.readOffline(translationCode.value, bookSlug.value, chapterNumber.value)
    message.value = chapter.value ? ru.offlineOpened : ru.offlineMissing
  })
}

async function testNotification(): Promise<void> {
  await run(async () => {
    const result = await schedulePrototypeNotification()
    message.value = result === 'scheduled'
      ? ru.notificationScheduled
      : result === 'denied'
        ? ru.notificationDenied
        : ru.notificationUnsupported
  })
}

async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true
  message.value = ru.loading
  try {
    await action()
  } catch (error) {
    message.value = error instanceof Error ? error.message : ru.unknownError
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="mobile-app">
    <header class="app-header">
      <div class="brand-lockup">
        <img src="/brand/bible-desktop-mark.png" alt="" />
        <span>
          <strong>{{ ru.brand }}</strong>
          <small>{{ ru.brandSubtitle }}</small>
        </span>
      </div>
      <div class="connection" :class="{ offline: !isOnline }">
        <span aria-hidden="true"></span>
        {{ isOnline ? ru.online : ru.offline }}
      </div>
    </header>

    <main class="app-content">
      <section class="hero">
        <p class="eyebrow">{{ ru.eyebrow }}</p>
        <h1>{{ ru.title }}</h1>
        <p class="intro">{{ ru.intro }}</p>
      </section>

      <section class="chapter-card" aria-labelledby="chapter-form-title">
        <header class="card-header">
          <span class="card-icon"><img src="/icons/library.png" alt="" /></span>
          <span>
            <small>{{ ru.cardEyebrow }}</small>
            <h2 id="chapter-form-title">{{ ru.cardTitle }}</h2>
          </span>
        </header>

        <div class="fields">
          <label class="translation-field">
            <span>{{ ru.translation }}</span>
            <input v-model.trim="translationCode" list="translation-options" autocomplete="off" />
            <datalist id="translation-options">
              <option value="BQ_RUSSIAN_RST_STRONG">Синодальный перевод</option>
            </datalist>
          </label>
          <label>
            <span>{{ ru.book }}</span>
            <input v-model.trim="bookSlug" list="book-options" autocomplete="off" />
            <datalist id="book-options">
              <option value="genesis">Бытие</option>
            </datalist>
          </label>
          <label>
            <span>{{ ru.chapter }}</span>
            <input v-model.number="chapterNumber" type="number" min="1" inputmode="numeric" />
          </label>
        </div>

        <button :disabled="busy" class="primary-action" type="button" @click="download">
          {{ busy ? ru.loading : ru.download }}
        </button>

        <p v-if="message" class="status" role="status" aria-live="polite">{{ message }}</p>
      </section>

      <article v-if="chapter" class="reading-card">
        <header>
          <p>{{ chapter.translation.name }}</p>
          <h2>{{ chapter.book.name }}, глава {{ chapter.chapter.number }}</h2>
        </header>
        <ol>
          <li v-for="verse in chapter.verses" :key="verse.id">
            <span class="verse-number">{{ verse.number }}</span>
            {{ verse.plain_text }}
          </li>
        </ol>
      </article>
    </main>

    <nav class="bottom-nav" aria-label="Основная навигация">
      <button class="active" type="button" aria-current="page">
        <img src="/icons/library.png" alt="" />
        <span>{{ ru.reading }}</span>
      </button>
      <button :disabled="busy" type="button" @click="openOffline">
        <img src="/icons/bookmarks.png" alt="" />
        <span>{{ ru.openOffline }}</span>
      </button>
      <button :disabled="busy" type="button" @click="testNotification">
        <img src="/icons/prayers.png" alt="" />
        <span>{{ ru.notification }}</span>
      </button>
    </nav>
  </div>
</template>
