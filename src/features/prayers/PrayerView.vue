<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { PrayerDetail } from '@/api/contracts'
import { bibleApi } from '@/api'
import {ApiError} from '@/api/client'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { createDailyContentService } from '@/services/dailyContentService'
import PrayerContent from '@/components/PrayerContent.vue'
import { useI18n } from '@/i18n'
import { prayerTextPresentation,prayerLanguageLabel } from '@/services/prayerEditions'
import {prayerCatalogMessages} from '@/i18n/prayerCatalog'

const route = useRoute()
const service = createDailyContentService(bibleApi, createIndexedDbDailyContentRepository())
const { language,messages: text } = useI18n()
const labels=computed(()=>prayerCatalogMessages[language.value])
const prayer = ref<PrayerDetail>()
const presentation = computed(() => prayer.value ? prayerTextPresentation(prayer.value) : undefined)
const message = ref('')

watch([()=>route.params.id,()=>route.query.language],async(_,__,cleanup) => {
  let stale=false;cleanup(()=>stale=true);prayer.value=undefined
  message.value = text.value.prayers.loadingPrayer
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id < 1) {
    message.value = text.value.prayers.notFound
    return
  }
  try {
    const result = await service.openPrayer(id,typeof route.query.language==='string'?route.query.language:undefined)
    if(stale)return
    prayer.value = result.data
    message.value = result.offline ? text.value.prayers.openedOffline : text.value.prayers.savedOffline
  } catch (error) {
    if(stale)return
    message.value=error instanceof ApiError&&error.status===404?text.value.prayers.notFound:text.value.prayers.openFailed
  }
},{immediate:true})
</script>

<template>
  <MobileShell back-to="/prayers">
    <article v-if="prayer && presentation" class="prayer-reading" :class="{ 'traditional-prayer': presentation.traditional, 'civil-prayer': presentation.civil }" :lang="presentation.language.startsWith('cu') ? 'cu' : presentation.language">
      <h1>{{ prayer.title }}</h1>
      <p>{{prayerLanguageLabel(presentation.language,text.setup)}}<template v-if="prayer.completeness==='complete'"> · {{labels.complete}}</template></p>
      <section v-if="prayer.intro" class="prayer-intro"><h2>{{labels.intro}}</h2><PrayerContent :content="prayer.intro" /></section>
      <PrayerContent :content="prayer.body" />
      <p class="status" role="status">{{ message }}</p>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>
