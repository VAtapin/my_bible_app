<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { bibleApi } from '@/api'
import { apiBaseUrl } from '@/config/api'
import { calendarAssetUrl, primaryTypikonMark, type CalendarGridDay } from '@/api/calendar'
import { calendarDateInTimeZone, calendarPeriodDates, formatCalendarDate, moveCalendarPeriod, isCalendarDate, type CalendarViewMode } from '@/services/calendarDates'
import { useI18n } from '@/i18n'
import { interfaceLocales } from '@/i18n/locale'
import { calendarContentLanguage } from '@/services/calendarContent'
import OfflineImage from './OfflineImage.vue'
import {calendarMealMark} from '@/services/calendarPresentation'

const props = withDefaults(defineProps<{ date: string; calendarLanguage: string; mode?: CalendarViewMode; compact?: boolean }>(), { mode: 'month', compact: false })
const emit = defineEmits<{ select: [date: string] }>()
const { language, messages: text } = useI18n()
const days = ref<CalendarGridDay[]>([])
const busy = ref(false)
const failed = ref(false)
const locale = computed(() => interfaceLocales[language.value])
const dates = computed(() => calendarPeriodDates(props.date, props.mode))
const offset = computed(() => props.mode === 'month' ? (new Date(`${dates.value[0]}T12:00:00Z`).getUTCDay() + 6) % 7 : 0)
const title = computed(() => props.mode === 'month'
  ? new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${props.date}T12:00:00Z`))
  : props.mode === 'week' ? new Intl.DateTimeFormat(locale.value, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).formatRange(new Date(`${dates.value[0]}T12:00:00Z`), new Date(`${dates.value.at(-1)}T12:00:00Z`)) : formatCalendarDate(props.date, locale.value))
const weekdays = computed(() => Array.from({ length: 7 }, (_, index) => new Intl.DateTimeFormat(locale.value, { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(2026, 0, 5 + index)))))
const lookup = computed(() => new Map(days.value.map((day) => [day.date, day])))
const move = (direction: number) => {
  const next = moveCalendarPeriod(props.date, direction, props.mode)
  if (isCalendarDate(next)) emit('select', next)
}
let generation = 0
watch(() => [dates.value[0].slice(0, 7), dates.value.at(-1)!.slice(0, 7), props.calendarLanguage], async () => {
  const requestGeneration = ++generation
  days.value = []
  busy.value = true
  failed.value = false
  try {
    const months = [...new Set([dates.value[0].slice(0, 7), dates.value.at(-1)!.slice(0, 7)])].filter((month) => isCalendarDate(`${month}-01`))
    const result = await Promise.all(months.map((month) => bibleApi.getCalendarMonth(`${month}-01`, calendarContentLanguage(props.calendarLanguage))))
    if (requestGeneration === generation) days.value = result.flat()
  } catch { if (requestGeneration === generation) failed.value = true }
  finally { if (requestGeneration === generation) busy.value = false }
}, { immediate: true })
onUnmounted(() => { generation++ })
function mark(date: string) {
  const marker = primaryTypikonMark(lookup.value.get(date))
  return marker ? { label: marker.label, url: calendarAssetUrl(marker.svgSource, apiBaseUrl) } : undefined
}
function label(date: string): string {
  const day = lookup.value.get(date)
  return [formatCalendarDate(date, locale.value), day?.weekday === 0 ? text.value.calendar.sunday : '', day?.foodLabel, ...(day?.events.map((event) => event.title) ?? [])].filter(Boolean).join('. ')
}
</script>

<template>
  <section class="calendar-picker" :class="{ compact }" :style="{ '--calendar-rows': Math.ceil((offset + dates.length) / 7) }" :aria-label="text.calendar.view" :aria-busy="busy">
    <div class="calendar-picker-heading">
      <button type="button" :aria-label="text.calendar.previous" @click="move(-1)">‹</button>
      <strong>{{ title }}</strong>
      <button type="button" :aria-label="text.calendar.next" @click="move(1)">›</button>
      <button type="button" class="calendar-now" @click="emit('select', calendarDateInTimeZone())">{{ text.navigation.today }}</button>
    </div>
    <div v-if="mode !== 'day'" class="calendar-grid" :class="{ 'calendar-week-list': mode === 'week' && !compact }">
      <small v-for="(weekday, index) in weekdays" :key="weekday" :class="{ 'calendar-sunday': index === 6 }">{{ weekday }}</small>
      <span v-for="blank in offset" :key="`blank-${blank}`"></span>
      <button v-for="item in dates" :key="item" type="button" :title="label(item)" :aria-label="label(item)"
        :aria-current="item === calendarDateInTimeZone() ? 'date' : undefined" :aria-pressed="item === date"
        :class="{ selected: item === date, today: item === calendarDateInTimeZone(), 'calendar-red': ['pascha', 'great-feast', 'sunday'].includes(lookup.get(item)?.dayStyle.rank ?? ''), 'calendar-gold': lookup.get(item)?.dayStyle.rank === 'monastery-feast' }"
        @click="emit('select', item)">
        <span class="grid-date-number">{{ Number(item.slice(-2)) }}</span>
        <svg v-if="calendarMealMark(lookup.get(item))" class="calendar-meal-mark" viewBox="0 0 24 24" role="img" :aria-label="calendarMealMark(lookup.get(item))!.label"><title>{{calendarMealMark(lookup.get(item))!.label}}</title><path d="M20 4C10 3 3 8 5 15c2 5 11 4 14-5l1-6ZM6 18l10-9"/></svg>
        <OfflineImage v-if="mark(item)?.url" class="typikon-grid-mark" :src="mark(item)!.url!" :alt="mark(item)!.label" />
        <small v-if="!compact && lookup.get(item)" class="grid-old-style">{{ Number(lookup.get(item)!.oldStyleDate.slice(-2)) }}</small>
        <span v-if="mode === 'week' && !compact" class="week-day-summary"><strong>{{ weekdays[(new Date(`${item}T12:00:00Z`).getUTCDay() + 6) % 7] }} · {{ lookup.get(item)?.foodLabel }}</strong><span>{{ lookup.get(item)?.events.slice(0, 2).map((event) => event.title).join(' · ') }}</span></span>
      </button>
    </div>
    <small v-if="busy || failed" class="calendar-grid-status" role="status">{{ busy ? text.calendar.loading : text.calendar.failed }}</small>
  </section>
</template>
<style scoped>.calendar-meal-mark{position:absolute;left:3px;bottom:3px;width:12px;height:12px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}.calendar-week-list .calendar-meal-mark{position:static;grid-column:2;grid-row:2;flex-shrink:0;width:16px;height:16px}</style>
