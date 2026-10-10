<script setup lang="ts">
import type {CalendarTextMetadata} from '@/api/automaticCalendar'
import {useI18n} from '@/i18n'
import {calendarTextPriorityMessages} from '@/i18n/calendarTextPriority'
import {prayerLanguageLabel} from '@/services/prayerEditions'
import {normalizePrayerText} from '@/services/prayerContent'
defineProps<{item:CalendarTextMetadata&{text:string}}>()
const {language,messages}=useI18n()
</script>
<template>
  <p v-if="item.selection==='missing'" role="status">{{calendarTextPriorityMessages[language].missing}}</p>
  <template v-else>
    <small>{{prayerLanguageLabel(item.language!,messages.setup)}}<template v-if="item.edition"> · {{item.edition}}</template></small>
    <p :class="{'slavonic-unicode':item.language==='cu','slavonic-civil':item.language==='cu-civil'}"
       :lang="item.language?.startsWith('cu')?'cu':item.language??undefined">{{normalizePrayerText(item.text)}}</p>
  </template>
</template>

<style scoped>
.slavonic-unicode{font-family:Ponomar,serif}.slavonic-civil{font-family:'Monomakh Unicode',serif;font-synthesis:none}
</style>
