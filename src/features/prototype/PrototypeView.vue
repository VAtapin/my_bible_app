<script setup lang="ts">
import { ref } from 'vue'
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
    message.value = error instanceof Error ? error.message : 'Неизвестная ошибка.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="shell">
    <section class="hero">
      <p class="eyebrow">{{ ru.eyebrow }}</p>
      <h1>{{ ru.title }}</h1>
      <p class="intro">{{ ru.intro }}</p>
    </section>

    <section class="panel" aria-labelledby="chapter-form-title">
      <h2 id="chapter-form-title">Параметры главы</h2>
      <div class="fields">
        <label>
          <span>{{ ru.translation }}</span>
          <input v-model.trim="translationCode" autocomplete="off" />
        </label>
        <label>
          <span>{{ ru.book }}</span>
          <input v-model.trim="bookSlug" autocomplete="off" />
        </label>
        <label>
          <span>{{ ru.chapter }}</span>
          <input v-model.number="chapterNumber" type="number" min="1" inputmode="numeric" />
        </label>
      </div>

      <div class="actions">
        <button :disabled="busy" type="button" @click="download">{{ ru.download }}</button>
        <button :disabled="busy" class="secondary" type="button" @click="openOffline">
          {{ ru.openOffline }}
        </button>
        <button :disabled="busy" class="quiet" type="button" @click="testNotification">
          {{ ru.notification }}
        </button>
      </div>

      <p class="status" role="status" aria-live="polite">{{ message }}</p>
    </section>

    <article v-if="chapter" class="reading">
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
</template>
