<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useRoute } from 'vue-router'
import { bibleApi } from '@/api'
import {ApiError} from '@/api/client'
import type { LiturgicalWorkVersion,LiturgicalWorkSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { normalizePrayerText } from '@/services/prayerContent'
import { prayerLanguageLabel } from '@/services/prayerEditions'
import {createDailyContentService} from '@/services/dailyContentService'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import PrayerContent from '@/components/PrayerContent.vue'
import {prayerCatalogMessages} from '@/i18n/prayerCatalog'

const route = useRoute()
const { language,messages: text } = useI18n()
const labels=computed(()=>prayerCatalogMessages[language.value])
const service=createDailyContentService(bibleApi,createIndexedDbDailyContentRepository())
const work = ref<LiturgicalWorkVersion>()
const metadata=ref<LiturgicalWorkSummary>()
const message = ref('')
const visibleBlocks = computed(() => work.value?.blocks.filter((block) => block.text.trim()).map((block) => ({ ...block, text: normalizePrayerText(block.text) })) ?? [])

watch([()=>route.params.slug,()=>route.params.language,()=>route.query.edition],async(_,__,cleanup) => {
  let stale=false;cleanup(()=>stale=true);work.value=undefined;metadata.value=undefined
  const slug = String(route.params.slug ?? '')
  const workLanguage = String(route.params.language ?? '')
  if (!slug || !workLanguage) {
    message.value = text.value.prayers.notFound
    return
  }
  message.value = text.value.prayers.loadingPrayer
  try {
    const result=await service.openLiturgicalVersion(slug,workLanguage,typeof route.query.edition==='string'?route.query.edition:undefined)
    if(stale)return
    work.value=result.data;metadata.value=result.metadata
    message.value=result.offline?text.value.prayers.openedOffline:text.value.prayers.savedOffline
  } catch (error) {
    if(stale)return
    message.value=error instanceof ApiError&&error.status===404?text.value.prayers.notFound:text.value.prayers.openFailed
  }
},{immediate:true})
</script>

<template>
  <MobileShell back-to="/prayers" reading>
    <article v-if="work" class="prayer-reading liturgical-reading" :class="{ 'traditional-prayer': work.orthography === 'traditional' && work.language === 'cu', 'civil-prayer': work.language.startsWith('cu') && ['civil', 'civil-accented'].includes(work.orthography) }" :lang="work.language.startsWith('cu') ? 'cu' : work.language">
      <p class="eyebrow dark-eyebrow">{{ prayerLanguageLabel(work.language, text.setup) }}</p>
      <h1>{{ work.title }}</h1>
      <p v-if="work.completeness==='complete'">{{labels.complete}}</p>
      <section v-if="metadata?.intro" class="prayer-intro"><h2>{{labels.intro}}</h2><PrayerContent :content="metadata.intro" /></section>
      <template v-for="block in visibleBlocks" :key="block.id">
        <h2 v-if="block.kind === 'heading'" class="prayer-text-heading">{{ block.text }}</h2>
        <p v-else :class="{ rubric: block.kind === 'rubric' }">{{ block.text }}</p>
      </template>
      <p class="status" role="status">{{message}}</p>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>
