<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { CalendarDay, CalendarEvent, CalendarReading } from '@/api/contracts'
import { bibleApi } from '@/api'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { addCalendarDays, calendarDateInTimeZone, formatCalendarDate } from '@/services/calendarDates'
import { createDailyContentService } from '@/services/dailyContentService'
import { readerTarget } from '@/services/bibleReferences'
import { useProfileStore } from '@/stores/profileStore'

const repository = createIndexedDbDailyContentRepository()
const service = createDailyContentService(bibleApi, repository)
const profile = useProfileStore()
const date = ref(calendarDateInTimeZone())
const day = ref<CalendarDay>()
const message = ref('Загружаем календарь…')
const busy = ref(false)
const horizonProgress = ref(0)
const horizonController = ref<AbortController>()

const events = computed(() => {
  const values = day.value?.events ?? []
  return profile.configuration?.calendar.level === 'major'
    ? values.filter((event) => event.type || event.is_icon_commemoration)
    : values
})

onMounted(async () => {
  profile.load()
  await openDay()
})

async function openDay(): Promise<void> {
  busy.value = true
  try {
    const result = await service.openCalendarDay(date.value)
    day.value = result.data
    message.value = result.offline ? 'Нет сети — открыт сохранённый день.' : 'День сохранён для офлайна.'
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Не удалось открыть календарь.'
  } finally {
    busy.value = false
  }
}

async function moveDay(offset: number): Promise<void> {
  date.value = addCalendarDays(date.value, offset)
  await openDay()
}

function readingLink(reading: CalendarReading): { path: string; query: Record<string, string> } | undefined {
  const passage = reading.reading?.passages[0]
  const target = passage && readerTarget(passage.book, passage.start.chapter)
  return target ? { path: '/reader', query: target } : undefined
}

function fastingNote(event: CalendarEvent): string {
  const metadata = event.metadata
  return metadata && !Array.isArray(metadata) && typeof metadata.meal_note === 'string'
    ? metadata.meal_note
    : event.name
}

async function downloadHorizon(): Promise<void> {
  horizonController.value = new AbortController()
  horizonProgress.value = 0
  busy.value = true
  message.value = 'Сохраняем календарь на 30 дней…'
  try {
    await service.downloadCalendarHorizon(date.value, 30, (current) => { horizonProgress.value = current }, horizonController.value.signal)
    message.value = 'Календарь на 30 дней доступен без сети.'
  } catch (error) {
    message.value = error instanceof DOMException && error.name === 'AbortError'
      ? 'Загрузка календаря остановлена.'
      : error instanceof Error ? error.message : 'Не удалось сохранить календарь.'
  } finally {
    busy.value = false
    horizonController.value = undefined
  }
}

function stopHorizonDownload(): void {
  horizonController.value?.abort()
}
</script>

<template>
  <MobileShell>
    <section class="calendar-hero">
      <p class="eyebrow">Церковный календарь</p>
      <div class="calendar-date-row">
        <button type="button" :disabled="busy" aria-label="Предыдущий день" @click="moveDay(-1)">←</button>
        <span><h1>{{ formatCalendarDate(date) }}</h1><small v-if="day">Старый стиль: {{ formatCalendarDate(day.old_style_date) }}</small></span>
        <button type="button" :disabled="busy" aria-label="Следующий день" @click="moveDay(1)">→</button>
      </div>
      <p v-if="day">{{ day.liturgical_period }}</p>
    </section>

    <p v-if="message" class="status" role="status">{{ message }}</p>

    <template v-if="day">
      <section v-if="day.fasting_events.length" class="calendar-section fasting-card">
        <h2>Пост</h2>
        <p v-for="item in day.fasting_events" :key="item.id">{{ fastingNote(item) }}</p>
      </section>

      <section class="calendar-section">
        <h2>Память дня</h2>
        <div class="calendar-list">
          <article v-for="event in events" :key="event.id">
            <strong>{{ event.name }}</strong>
            <small v-if="event.type">{{ event.type.name }}</small>
          </article>
        </div>
      </section>

      <section class="calendar-section">
        <h2>Чтения дня</h2>
        <div class="calendar-list">
          <article v-for="reading in day.readings" :key="reading.id" class="reading-link-card">
            <span><strong>{{ reading.display_ref || reading.title }}</strong><small>{{ reading.title }}</small></span>
            <RouterLink v-if="readingLink(reading)" :to="readingLink(reading)!">Открыть</RouterLink>
          </article>
        </div>
      </section>

      <section class="calendar-offline-card">
        <span><strong>Офлайн-горизонт</strong><small>Сохраните текущий и следующие 29 дней</small></span>
        <button v-if="!horizonController" type="button" :disabled="busy" @click="downloadHorizon">Скачать</button>
        <button v-else type="button" @click="stopHorizonDownload">Стоп</button>
        <progress v-if="horizonProgress" :value="horizonProgress" max="30">{{ horizonProgress }}/30</progress>
      </section>
    </template>
  </MobileShell>
</template>
