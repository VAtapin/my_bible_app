<script setup lang="ts">
import type { AutomaticCalendarPlan } from '@/api/automaticCalendar'
import { useI18n } from '@/i18n'
import CalendarServiceText from './CalendarServiceText.vue'
defineProps<{ plan: AutomaticCalendarPlan }>()
const { messages: text } = useI18n()
</script>
<template>
      <div class="calendar-service-list">
        <article v-for="(item, index) in plan.assignments" :key="`${item.textId}-${index}`">
          <h3>{{ item.title }} <small>{{ item.slot === 'troparion-of-day' ? text.calendar.troparion : item.slot === 'kontakion-of-day' ? text.calendar.kontakion : '' }}</small></h3>
          <CalendarServiceText :item="item"/>
        </article>
        <article v-for="item in plan.expansions" :key="item.id">
          <h3>{{ item.title }}</h3>
          <CalendarServiceText :item="item"/>
        </article>
      </div>
</template>
