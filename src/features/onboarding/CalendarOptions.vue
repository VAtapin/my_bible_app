<script setup lang="ts">
import { useI18n } from '@/i18n'
import type { CalendarHomeSettings, CalendarLevel } from '@/profile/configuration'
const level = defineModel<CalendarLevel>('level', { required: true })
const home = defineModel<CalendarHomeSettings>('home', { required: true })
const { messages: text } = useI18n()
const keys = ['fasting', 'commemorations', 'readings', 'compact'] as const
</script>
<template>
  <div class="option-group">
    <h3>{{ text.setup.calendarTitle }}</h3>
    <label class="option-row radio-row"><input v-model="level" type="radio" value="major" /><span>{{ text.setup.calendarMajor }}</span></label>
    <label class="option-row radio-row"><input v-model="level" type="radio" value="all" /><span>{{ text.setup.calendarAll }}</span></label>
    <h3 class="suboption-title">{{ text.setup.homeDisplay }}</h3>
    <label v-for="key in keys" :key="key" class="toggle-row">
      <span>{{ text.setup.calendarHome[key] }}</span><input v-model="home[key]" type="checkbox" />
    </label>
  </div>
</template>
