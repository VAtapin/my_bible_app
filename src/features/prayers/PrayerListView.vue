<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { bibleApi } from '@/api'
import type { PrayerSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { useProfileStore } from '@/stores/profileStore'

const repository = createIndexedDbDailyContentRepository()
const profile = useProfileStore()
const prayers = ref<PrayerSummary[]>([])
const message = ref('Загружаем молитвослов…')
const displayPrayers = computed(() => [...prayers.value].sort((left, right) => rulePriority(left) - rulePriority(right)))

onMounted(async () => {
  profile.load()
  try {
    prayers.value = await bibleApi.getPrayers('ru')
    message.value = ''
  } catch (error) {
    const saved = await repository.listPrayers()
    prayers.value = saved.map(({ data }) => ({ ...data, excerpt: data.intro ?? contentExcerpt(data.body) }))
    message.value = saved.length
      ? 'Нет сети — показаны сохранённые молитвы.'
      : error instanceof Error ? error.message : 'Не удалось открыть молитвослов.'
  }
})

function rulePriority(prayer: PrayerSummary): number {
  const settings = profile.configuration?.prayers
  if (settings?.morning && /утрен/i.test(prayer.title)) return 0
  if (settings?.evening && /(сон грядущим|вечер)/i.test(prayer.title)) return 1
  return 2
}

function ruleLabel(prayer: PrayerSummary): string | undefined {
  const priority = rulePriority(prayer)
  return priority === 0 ? 'Утреннее правило' : priority === 1 ? 'Вечернее правило' : undefined
}

function contentExcerpt(value: string): string {
  return value.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120)
}
</script>

<template>
  <MobileShell>
    <section class="reader-heading">
      <span class="card-icon"><img src="/app-icons/prayers.png" alt="" /></span>
      <span><p class="eyebrow dark-eyebrow">Ежедневная молитва</p><h1>Молитвослов</h1></span>
    </section>
    <p v-if="message" class="status" role="status">{{ message }}</p>
    <section v-if="prayers.length" class="content-catalog">
      <RouterLink v-for="prayer in displayPrayers" :key="prayer.id" class="content-card" :to="`/prayers/${prayer.id}`">
        <span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span>
        <span><em v-if="ruleLabel(prayer)">{{ ruleLabel(prayer) }}</em><strong>{{ prayer.title }}</strong><small>{{ prayer.excerpt }}</small></span>
        <span aria-hidden="true">→</span>
      </RouterLink>
    </section>
  </MobileShell>
</template>
