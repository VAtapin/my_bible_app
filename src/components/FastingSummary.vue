<script setup lang="ts">
import type { CalendarDay } from '@/api/contracts'
import { fastingNote } from '@/services/calendarPresentation'
import { useI18n } from '@/i18n'
defineProps<{ day: CalendarDay; compact?: boolean }>()
const { messages: text } = useI18n()
</script>
<template>
  <div v-if="day.food || day.fasting_events.length" class="fasting-summary" :class="{ compact }">
    <img v-if="day.food?.image_url" :src="day.food.image_url" :alt="day.food.label" />
    <span v-if="day.food"><strong>{{ day.food.label }}</strong><p v-if="!compact">{{ day.food.reason }}</p></span>
    <span v-else><strong>{{ text.calendar.fasting }}</strong><p v-for="item in day.fasting_events" :key="item.id">{{ fastingNote(item) }}</p></span>
    <img v-for="marker in day.memorial_markers" :key="marker.image_url" :src="marker.image_url" :alt="marker.label" />
  </div>
</template>
