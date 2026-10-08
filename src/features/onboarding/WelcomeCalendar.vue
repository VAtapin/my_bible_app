<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { bibleApi, kalendarApi } from '@/api'
import type { CalendarDay } from '@/api/contracts'
import CalendarGrid from '@/components/CalendarGrid.vue'
import DayIcon from '@/components/DayIcon.vue'
import OfflineImage from '@/components/OfflineImage.vue'
import FastingSummary from '@/components/FastingSummary.vue'
import { useI18n } from '@/i18n'
import { interfaceLocales } from '@/i18n/locale'
import { calendarDateInTimeZone, formatTodayDate } from '@/services/calendarDates'
import { calendarContentLanguage, createCalendarContentService } from '@/services/kalendarContent'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { rankedCalendarIcons } from '@/services/calendarIcons'
const { language, messages: text } = useI18n()
const date = ref(calendarDateInTimeZone())
const day = ref<CalendarDay>()
const loading = ref(false), failed = ref(false)
const calendarLanguage = computed(() => calendarContentLanguage(language.value))
const service = createCalendarContentService(kalendarApi, bibleApi, createIndexedDbDailyContentRepository())
const icon = computed(() => day.value ? rankedCalendarIcons(day.value)[0] : undefined)
let generation = 0
watch(() => [date.value, calendarLanguage.value], async () => {
  const current = ++generation
  day.value = undefined; failed.value = false; loading.value = true
  try { const result = await service.openCalendarDay(date.value, calendarLanguage.value); if (generation === current) day.value = result.data }
  catch { if (generation === current) failed.value = true }
  finally { if (generation === current) loading.value = false }
}, { immediate: true })
onUnmounted(() => { generation++ })
</script>
<template>
  <section class="welcome-calendar" :aria-label="text.calendar.eyebrow">
    <h2>{{ text.calendar.eyebrow }}</h2>
    <CalendarGrid :date="date" :calendar-language="calendarLanguage" compact @select="date = $event" />
    <h3>{{ formatTodayDate(date, day?.old_style_date, interfaceLocales[language]) }}</h3>
    <p v-if="loading || failed" role="status">{{ loading ? text.calendar.loading : text.calendar.failed }}</p>
    <template v-if="day">
      <div class="welcome-calendar-day">
        <DayIcon v-if="icon" :key="`${date}-${icon.id}`" :icon="icon" />
        <div><p v-for="event in day.events.slice(0, 3)" :key="event.id"><OfflineImage v-if="event.typikon_mark" class="typikon-event-mark" :src="event.typikon_mark.image_url" :alt="event.typikon_mark.label" />{{ event.name }}</p></div>
      </div>
      <FastingSummary :day="day" compact />
      <RouterLink :to="{ path: '/calendar', query: { date } }">{{ text.today.details }} →</RouterLink>
    </template>
  </section>
</template>
