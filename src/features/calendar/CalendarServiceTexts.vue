<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { bibleApi } from '@/api'
import type { AutomaticCalendarPlan } from '@/api/automaticCalendar'
import { useI18n } from '@/i18n'
import {createCalendarContentService} from '@/services/calendarContent'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import CalendarServiceTextsContent from './CalendarServiceTextsContent.vue'
const props = defineProps<{ date: string; calendarLanguage: string }>()
const { messages: text } = useI18n()
const service=createCalendarContentService(bibleApi,createIndexedDbDailyContentRepository())
const plan = ref<AutomaticCalendarPlan>()
const failed = ref(false)
const loading = ref(false)
let generation = 0
watch(() => [props.date, props.calendarLanguage], async () => {
  const current = ++generation
  plan.value = undefined
  failed.value = false
  loading.value = true
  try {
    const result = await service.openAutomaticService(props.date,props.calendarLanguage)
    if (current === generation) plan.value = result.data
  } catch {
    if (current === generation) failed.value=true
  }
  finally { if (current === generation) loading.value = false }
}, { immediate: true })
onUnmounted(() => { generation++ })
</script>
<template>
  <section class="calendar-section">
    <h2>{{ text.calendar.serviceTexts }}</h2>
    <p v-if="loading || failed" class="reader-status" role="status">{{ loading ? text.calendar.loading : text.calendar.serviceFailed }}</p>
    <CalendarServiceTextsContent v-if="plan" :plan="plan"/>
  </section>
</template>
