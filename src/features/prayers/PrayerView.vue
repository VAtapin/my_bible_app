<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { PrayerDetail } from '@/api/contracts'
import { bibleApi } from '@/api'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { createDailyContentService } from '@/services/dailyContentService'
import { contentText } from '@/services/contentText'
import { useI18n } from '@/i18n'

const route = useRoute()
const service = createDailyContentService(bibleApi, createIndexedDbDailyContentRepository())
const { messages: text } = useI18n()
const prayer = ref<PrayerDetail>()
const message = ref('')
const body = computed(() => prayer.value ? contentText(prayer.value.body) : '')

onMounted(async () => {
  message.value = text.value.prayers.loadingPrayer
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id < 1) {
    message.value = text.value.prayers.notFound
    return
  }
  try {
    const result = await service.openPrayer(id)
    prayer.value = result.data
    message.value = result.offline ? text.value.prayers.openedOffline : text.value.prayers.savedOffline
  } catch (error) {
    message.value = error instanceof Error ? error.message : text.value.prayers.openFailed
  }
})
</script>

<template>
  <MobileShell back-to="/prayers">
    <article v-if="prayer" class="prayer-reading">
      <p class="eyebrow dark-eyebrow">{{ prayer.category }}</p>
      <h1>{{ prayer.title }}</h1>
      <p v-if="prayer.intro" class="prayer-intro">{{ prayer.intro }}</p>
      <p class="prayer-body">{{ body }}</p>
      <p class="status" role="status">{{ message }}</p>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>
