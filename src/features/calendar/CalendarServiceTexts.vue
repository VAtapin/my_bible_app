<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { bibleApi } from '@/api'
import type { AutomaticCalendarPlan } from '@/api/automaticCalendar'
import { useI18n } from '@/i18n'
import {createCalendarContentService} from '@/services/calendarContent'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {calendarTextPriorityMessages} from '@/i18n/calendarTextPriority'
import CalendarServiceText from './CalendarServiceText.vue'
import CalendarTextReader from './CalendarTextReader.vue'
const props = defineProps<{ date: string; calendarLanguage: string }>()
const { messages: text,language } = useI18n()
const service=createCalendarContentService(bibleApi,createIndexedDbDailyContentRepository())
const plan = ref<AutomaticCalendarPlan>()
const failed = ref(false)
const loading = ref(false)
const selectedText=ref<AutomaticCalendarPlan['assignments'][number]|AutomaticCalendarPlan['expansions'][number]>()
let generation = 0
watch(() => [props.date, props.calendarLanguage], async () => {
  const current = ++generation
  plan.value = undefined
  selectedText.value=undefined
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
    <p>{{calendarTextPriorityMessages[language].automatic}}</p>
    <p v-if="loading || failed" class="reader-status" role="status">{{ loading ? text.calendar.loading : text.calendar.serviceFailed }}</p>
    <template v-if="plan">
      <p v-if="plan.properCoverage?.message" class="calendar-event-description">{{ plan.properCoverage.message }}</p>
      <div class="calendar-service-list">
        <details v-for="(item, index) in plan.assignments" :key="`${item.textId}-${index}`">
          <summary>{{ item.title }} <small>{{ item.slot === 'troparion-of-day' ? text.calendar.troparion : item.slot === 'kontakion-of-day' ? text.calendar.kontakion : '' }}</small></summary>
          <small v-if="item.insert === false">{{ text.calendar.referenceText }}</small>
          <CalendarServiceText v-if="item.selection==='missing'" :item="item"/><button v-else type="button" @click="selectedText=item">{{text.calendar.open}}</button>
        </details>
        <details v-for="item in plan.expansions" :key="item.id">
          <summary>{{ item.title }}</summary>
          <CalendarServiceText v-if="item.selection==='missing'" :item="item"/><button v-else type="button" @click="selectedText=item">{{text.calendar.open}}</button>
        </details>
      </div>
    </template>
    <CalendarTextReader v-if="selectedText" :item="selectedText" @close="selectedText=undefined"/>
  </section>
</template>
