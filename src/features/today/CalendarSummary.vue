<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { CalendarDay } from '@/api/contracts'
import { useI18n } from '@/i18n'
import type { AppConfiguration } from '@/profile/configuration'
import { defaultCalendarHome } from '@/profile/configuration'
import { calendarEvents, calendarReadingLink, fastingNote } from '@/services/calendarPresentation'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ day?: CalendarDay; loading: boolean; failed: boolean; offline: boolean; settings: AppConfiguration['calendar'] }>()
const { messages: text } = useI18n()
const home = computed(() => props.settings.home ?? defaultCalendarHome())
const events = computed(() => calendarEvents(props.day?.events ?? [], props.settings.level))
const visibleEvents = computed(() => home.value.compact ? events.value.slice(0, 3) : events.value)
const readings = computed(() => home.value.compact ? props.day?.readings.slice(0, 2) ?? [] : props.day?.readings ?? [])
</script>
<template>
  <section class="day-summary">
    <p v-if="loading" role="status">{{ text.calendar.loading }}</p>
    <p v-else-if="failed" role="status">{{ text.calendar.failed }}</p>
    <template v-else-if="day">
      <small v-if="offline">{{ text.offline }}</small>
      <div v-if="home.fasting && day.fasting_events.length" class="day-fasting"><strong>{{ text.calendar.fasting }}</strong><p v-for="item in day.fasting_events" :key="item.id">{{ fastingNote(item) }}</p></div>
      <div v-if="home.commemorations && visibleEvents.length" class="day-events">
        <p v-for="event in visibleEvents" :key="event.id">{{ event.name }}</p>
        <RouterLink v-if="events.length > visibleEvents.length" to="/calendar">{{ text.today.allCommemorations }} →</RouterLink>
      </div>
      <div v-if="home.readings && readings.length" class="day-readings">
        <template v-for="reading in readings" :key="reading.id">
          <RouterLink v-if="calendarReadingLink(reading)" :to="calendarReadingLink(reading)!">{{ reading.display_ref || reading.title }} →</RouterLink>
          <span v-else>{{ reading.display_ref || reading.title }}</span>
        </template>
      </div>
    </template>
  </section>
</template>
