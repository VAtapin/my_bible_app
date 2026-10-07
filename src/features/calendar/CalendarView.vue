<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { CalendarDay } from '@/api/contracts'
import { bibleApi, kalendarApi } from '@/api'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { calendarDateInTimeZone, formatTodayDate, isCalendarDate, type CalendarViewMode } from '@/services/calendarDates'
import { rankedCalendarIcons } from '@/services/calendarIcons'
import DayIcon from '@/components/DayIcon.vue'
import CalendarGrid from '@/components/CalendarGrid.vue'
import FastingSummary from '@/components/FastingSummary.vue'
import CalendarServiceTexts from './CalendarServiceTexts.vue'
import { createCalendarContentService } from '@/services/kalendarContent'
import { calendarEvents, calendarReadingLink as readingLink } from '@/services/calendarPresentation'
import { useProfileStore } from '@/stores/profileStore'
import { useI18n } from '@/i18n'

const repository = createIndexedDbDailyContentRepository()
const service = createCalendarContentService(kalendarApi, bibleApi, repository)
const route = useRoute()
const router = useRouter()
const profile = useProfileStore()
const { language, messages: text } = useI18n()
const requestedDate = isCalendarDate(route.query.date) ? route.query.date : undefined
const date = ref(requestedDate ?? calendarDateInTimeZone())
const day = ref<CalendarDay>()
const message = ref('')
const busy = ref(false)
const horizonProgress = ref(0)
const horizonController = ref<AbortController>()

const events = computed(() => calendarEvents(day.value?.events ?? [], 'all'))
const icons = computed(() => day.value ? rankedCalendarIcons(day.value) : [])
const mode = ref<CalendarViewMode>('month')
const calendarLanguage = computed(() => profile.configuration?.calendar.languageCode ?? language.value)
let generation = 0

onMounted(async () => {
  profile.load()
  message.value = text.value.calendar.loading
  await openDay()
})

async function openDay(): Promise<void> {
  const requestGeneration = ++generation
  day.value = undefined
  message.value = text.value.calendar.loading
  busy.value = true
  try {
    const calendarLanguage = profile.configuration?.calendar.languageCode ?? language.value
    const result = await service.openCalendarDay(date.value, calendarLanguage)
    if (requestGeneration !== generation) return
    day.value = result.data
    message.value = result.offline ? text.value.calendar.offline : ''
  } catch (error) {
    if (requestGeneration !== generation) return
    message.value = text.value.calendar.failed
  } finally {
    if (requestGeneration === generation) busy.value = false
  }
}

async function selectDate(value: string): Promise<void> {
  date.value = value
  void router.replace({ query: { ...route.query, date: value } })
  await openDay()
}
onUnmounted(() => { generation++; horizonController.value?.abort() })

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
    <section class="calendar-controls">
      <div class="calendar-view-switch" role="group" :aria-label="text.calendar.view">
        <button v-for="option in (['day', 'week', 'month'] as const)" :key="option" type="button" :aria-pressed="mode === option" :class="{ active: mode === option }" @click="mode = option">{{ text.calendar[option] }}</button>
      </div>
      <CalendarGrid :date="date" :calendar-language="calendarLanguage" :mode="mode" @select="selectDate" />
    </section>

    <p v-if="message" class="status" role="status">{{ message }}</p>

    <template v-if="day">
      <h1 class="calendar-selected-date">{{ formatTodayDate(date, day.old_style_date, language === 'de' ? 'de-DE' : 'ru-RU') }}</h1>
      <div v-if="day.tone || day.week_after_pentecost" class="calendar-facts"><span v-if="day.tone">{{ text.calendar.tone }} {{ day.tone }}</span><span v-if="day.week_after_pentecost">{{ text.calendar.weekAfterPentecost }} {{ day.week_after_pentecost }}</span></div>
      <FastingSummary :day="day" />
      <section v-if="icons.length" class="calendar-icon-gallery">
        <figure v-for="icon in icons" :key="`${date}-${icon.id}`"><DayIcon :icon="icon" /><figcaption>{{ icon.title }}</figcaption></figure>
      </section>

      <section class="calendar-section">
        <h2>{{ text.calendar.commemorations }}</h2>
        <div class="calendar-list">
          <article v-for="event in events" :key="event.id">
            <strong><img v-if="event.typikon_mark" class="typikon-event-mark" :src="event.typikon_mark.image_url" :alt="event.typikon_mark.label" />{{ event.name }}</strong>
            <small v-if="event.type">{{ event.type.name }}</small>
            <p v-if="event.description" class="calendar-event-description">{{ event.description }}</p>
          </article>
        </div>
      </section>

      <section v-if="day.other_events?.length" class="calendar-section">
        <h2>{{ text.calendar.dayRules }}</h2>
        <div class="calendar-list"><article v-for="event in day.other_events" :key="event.id"><strong>{{ event.name }}</strong><p v-if="event.description" class="calendar-event-description">{{ event.description }}</p></article></div>
      </section>

      <section class="calendar-section">
        <h2>{{ text.calendar.readings }}</h2>
        <div class="calendar-list">
          <article v-for="reading in day.readings" :key="reading.id" class="reading-link-card">
            <span><strong>{{ reading.display_ref || reading.title }}</strong></span>
            <RouterLink v-if="readingLink(reading)" :to="readingLink(reading)!">{{ text.calendar.open }}</RouterLink>
          </article>
        </div>
      </section>

      <CalendarServiceTexts :date="date" :calendar-language="calendarLanguage" />
      <section class="calendar-offline-card">
        <span><strong>{{ text.calendar.horizon }}</strong><small>{{ text.calendar.horizonHint }}</small></span>
        <button v-if="!horizonController" type="button" :disabled="busy" @click="downloadHorizon">{{ text.calendar.download }}</button>
        <button v-else type="button" @click="stopHorizonDownload">{{ text.calendar.stop }}</button>
        <progress v-if="horizonProgress" :value="horizonProgress" max="30">{{ horizonProgress }}/30</progress>
      </section>
    </template>
  </MobileShell>
</template>
