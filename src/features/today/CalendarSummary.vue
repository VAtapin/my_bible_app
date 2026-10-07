<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { CalendarDay } from '@/api/contracts'
import { bibleApi } from '@/api'
import { useI18n } from '@/i18n'
import type { AppConfiguration } from '@/profile/configuration'
import { defaultCalendarHome } from '@/profile/configuration'
import { createDailyContentService } from '@/services/dailyContentService'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { calendarEvents, calendarReadingLink, fastingNote } from '@/services/calendarPresentation'
import { formatCalendarDate } from '@/services/calendarDates'

const props = defineProps<{ date: string; settings: AppConfiguration['calendar'] }>()
const { language, messages: text } = useI18n()
const service = createDailyContentService(bibleApi, createIndexedDbDailyContentRepository())
const day = ref<CalendarDay>()
const loading = ref(false)
const failed = ref(false)
const offline = ref(false)
const home = computed(() => props.settings.home ?? defaultCalendarHome())
const events = computed(() => calendarEvents(day.value?.events ?? [], props.settings.level))
const visibleEvents = computed(() => home.value.compact ? events.value.slice(0, 3) : events.value)
const readings = computed(() => home.value.compact ? day.value?.readings.slice(0, 2) ?? [] : day.value?.readings ?? [])
let generation = 0
watch(() => [props.date, props.settings.languageCode], async () => {
  const request = ++generation
  loading.value = true
  failed.value = false
  day.value = undefined
  try {
    const result = await service.openCalendarDay(props.date, props.settings.languageCode)
    if (request === generation) { day.value = result.data; offline.value = result.offline }
  } catch { if (request === generation) failed.value = true }
  finally { if (request === generation) loading.value = false }
}, { immediate: true })
</script>
<template>
  <section class="day-summary">
    <header class="section-heading-row"><h2>{{ text.today.calendarDay }}</h2><RouterLink to="/calendar">{{ text.today.details }} →</RouterLink></header>
    <p v-if="loading" role="status">{{ text.calendar.loading }}</p>
    <p v-else-if="failed" role="status">{{ text.calendar.failed }}</p>
    <template v-else-if="day">
      <p class="day-period">{{ day.liturgical_period }}</p>
      <small v-if="home.oldStyle">{{ text.calendar.oldStyle }}: {{ formatCalendarDate(day.old_style_date, language === 'de' ? 'de-DE' : 'ru-RU') }}</small>
      <small v-if="offline"> · {{ text.offline }}</small>
      <div v-if="home.fasting && day.fasting_events.length" class="day-fasting"><strong>{{ text.calendar.fasting }}</strong><p v-for="item in day.fasting_events" :key="item.id">{{ fastingNote(item) }}</p></div>
      <div v-if="home.commemorations && visibleEvents.length" class="day-events">
        <strong>{{ text.calendar.commemorations }}</strong>
        <p v-for="event in visibleEvents" :key="event.id">{{ event.name }}</p>
        <RouterLink v-if="events.length > visibleEvents.length" to="/calendar">{{ text.today.allCommemorations }} →</RouterLink>
      </div>
      <div v-if="home.readings && readings.length" class="day-readings">
        <strong>{{ text.calendar.readings }}</strong>
        <template v-for="reading in readings" :key="reading.id">
          <RouterLink v-if="calendarReadingLink(reading)" :to="calendarReadingLink(reading)!">{{ reading.display_ref || reading.title }} →</RouterLink>
          <span v-else>{{ reading.display_ref || reading.title }}</span>
        </template>
      </div>
    </template>
  </section>
</template>
