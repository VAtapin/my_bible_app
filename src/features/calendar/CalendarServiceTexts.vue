<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { bibleApi } from '@/api'
import type { CalendarServicePlan } from '@/api/contracts'
import { useI18n } from '@/i18n'
import { normalizePrayerText } from '@/services/prayerContent'
import { readCalendarState } from '@/offline/calendarMedia'
import { calendarServiceKey } from '@/services/kalendarContent'
import { ApiError } from '@/api/client'
const props = defineProps<{ date: string; calendarLanguage: string }>()
const { messages: text } = useI18n()
const plan = ref<CalendarServicePlan>()
const failed = ref(false)
const loading = ref(false)
let generation = 0
watch(() => [props.date, props.calendarLanguage], async () => {
  const current = ++generation
  plan.value = undefined
  failed.value = false
  loading.value = true
  try {
    const result = await bibleApi.getCalendarService(props.date, props.calendarLanguage === 'cu' ? 'cu' : 'cu-civil')
    if (current === generation) plan.value = result
  } catch (error) {
    const saved = error instanceof ApiError && ['offline', 'timeout'].includes(error.kind)
      ? await readCalendarState<CalendarServicePlan>(calendarServiceKey(props.date, props.calendarLanguage === 'cu' ? 'cu' : 'cu-civil')).catch(() => undefined) : undefined
    if (current === generation) { plan.value = saved; failed.value = !saved }
  }
  finally { if (current === generation) loading.value = false }
}, { immediate: true })
onUnmounted(() => { generation++ })
</script>
<template>
  <section class="calendar-section">
    <h2>{{ text.calendar.serviceTexts }}</h2>
    <p v-if="loading || failed" class="reader-status" role="status">{{ loading ? text.calendar.loading : text.calendar.serviceFailed }}</p>
    <template v-if="plan">
      <p v-if="plan.properCoverage?.message" class="calendar-event-description">{{ plan.properCoverage.message }}</p>
      <div class="calendar-service-list" :class="{ 'slavonic-unicode': plan.textLanguage === 'cu', 'slavonic-civil': plan.textLanguage === 'cu-civil' }" :lang="plan.textLanguage.startsWith('cu') ? 'cu' : plan.textLanguage">
        <details v-for="(item, index) in plan.assignments" :key="`${item.textId}-${index}`">
          <summary>{{ item.title }} <small>{{ item.slot === 'troparion-of-day' ? text.calendar.troparion : item.slot === 'kontakion-of-day' ? text.calendar.kontakion : '' }}</small></summary>
          <small v-if="item.insert === false">{{ text.calendar.referenceText }}</small>
          <p v-if="item.rubric">{{ normalizePrayerText(item.rubric) }}</p><p>{{ normalizePrayerText(item.text) }}</p>
        </details>
        <details v-for="item in plan.expansions" :key="item.id"><summary>{{ item.title }}</summary><p>{{ normalizePrayerText(item.text) }}</p></details>
      </div>
    </template>
  </section>
</template>
