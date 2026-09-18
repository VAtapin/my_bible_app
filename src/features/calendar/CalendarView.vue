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
import { useI18n } from '@/i18n'

const repository = createIndexedDbDailyContentRepository()
const service = createDailyContentService(bibleApi, repository)
const profile = useProfileStore()
const { language, messages: text } = useI18n()
const date = ref(calendarDateInTimeZone())
const day = ref<CalendarDay>()
const message = ref('')
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
  message.value = text.value.calendar.loading
  await openDay()
})

async function openDay(): Promise<void> {
  busy.value = true
  try {
    const calendarLanguage = profile.configuration?.calendar.languageCode ?? language.value
    const result = await service.openCalendarDay(date.value, calendarLanguage)
    day.value = result.data
    message.value = result.offline ? text.value.calendar.offline : text.value.calendar.saved
  } catch (error) {
    message.value = error instanceof Error ? error.message : text.value.calendar.failed
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
  message.value = text.value.calendar.savingHorizon
  try {
    const calendarLanguage = profile.configuration?.calendar.languageCode ?? language.value
    await service.downloadCalendarHorizon(date.value, 30, (current) => { horizonProgress.value = current }, horizonController.value.signal, calendarLanguage)
    message.value = text.value.calendar.horizonSaved
  } catch (error) {
    message.value = error instanceof DOMException && error.name === 'AbortError'
      ? text.value.calendar.horizonStopped
      : error instanceof Error ? error.message : text.value.calendar.horizonFailed
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
      <p class="eyebrow">{{ text.calendar.eyebrow }}</p>
      <div class="calendar-date-row">
        <button type="button" :disabled="busy" :aria-label="text.calendar.previous" @click="moveDay(-1)">←</button>
        <span><h1>{{ formatCalendarDate(date, language === 'de' ? 'de-DE' : 'ru-RU') }}</h1><small v-if="day">{{ text.calendar.oldStyle }}: {{ formatCalendarDate(day.old_style_date, language === 'de' ? 'de-DE' : 'ru-RU') }}</small></span>
        <button type="button" :disabled="busy" :aria-label="text.calendar.next" @click="moveDay(1)">→</button>
      </div>
      <p v-if="day">{{ day.liturgical_period }}</p>
    </section>

    <p v-if="message" class="status" role="status">{{ message }}</p>

    <template v-if="day">
      <section v-if="day.fasting_events.length" class="calendar-section fasting-card">
        <h2>{{ text.calendar.fasting }}</h2>
        <p v-for="item in day.fasting_events" :key="item.id">{{ fastingNote(item) }}</p>
      </section>

      <section class="calendar-section">
        <h2>{{ text.calendar.commemorations }}</h2>
        <div class="calendar-list">
          <article v-for="event in events" :key="event.id">
            <strong>{{ event.name }}</strong>
            <small v-if="event.type">{{ event.type.name }}</small>
          </article>
        </div>
      </section>

      <section class="calendar-section">
        <h2>{{ text.calendar.readings }}</h2>
        <div class="calendar-list">
          <article v-for="reading in day.readings" :key="reading.id" class="reading-link-card">
            <span><strong>{{ reading.display_ref || reading.title }}</strong><small>{{ reading.title }}</small></span>
            <RouterLink v-if="readingLink(reading)" :to="readingLink(reading)!">{{ text.calendar.open }}</RouterLink>
          </article>
        </div>
      </section>

      <section class="calendar-offline-card">
        <span><strong>{{ text.calendar.horizon }}</strong><small>{{ text.calendar.horizonHint }}</small></span>
        <button v-if="!horizonController" type="button" :disabled="busy" @click="downloadHorizon">{{ text.calendar.download }}</button>
        <button v-else type="button" @click="stopHorizonDownload">{{ text.calendar.stop }}</button>
        <progress v-if="horizonProgress" :value="horizonProgress" max="30">{{ horizonProgress }}/30</progress>
      </section>
    </template>
  </MobileShell>
</template>
