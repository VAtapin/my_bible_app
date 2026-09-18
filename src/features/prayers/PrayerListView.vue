<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { bibleApi } from '@/api'
import type { LiturgicalWorkSummary, PrayerSummary } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { useProfileStore } from '@/stores/profileStore'

interface WorkCard extends LiturgicalWorkSummary {
  language: string
  collection: 'akathists' | 'canons' | 'horologion'
}

const repository = createIndexedDbDailyContentRepository()
const profile = useProfileStore()
const { messages: text } = useI18n()
const prayers = ref<PrayerSummary[]>([])
const works = ref<WorkCard[]>([])
const message = ref('')
const displayPrayers = computed(() => [...prayers.value].sort((left, right) => rulePriority(left) - rulePriority(right)))

onMounted(async () => {
  const configuration = profile.load()
  const settings = configuration?.prayers
  const languages = settings?.languageCodes ?? [configuration?.interfaceLanguage ?? 'ru']
  message.value = text.value.prayers.loading

  try {
    const prayerResults = await Promise.all(languages.map((language) => bibleApi.getPrayers(language)))
    prayers.value = prayerResults.flat()
      .filter((prayer) => languages.includes(prayer.language_code))
      .filter(matchesPrayerSettings)

    const collections = [
      settings?.akathists ? 'akathists' : null,
      settings?.canons ? 'canons' : null,
      settings?.horologion ? 'horologion' : null,
    ].filter((value): value is WorkCard['collection'] => value !== null)
    const workResults = await Promise.all(collections.map(async (collection) => ({
      collection,
      items: await bibleApi.getLiturgicalWorks(collection),
    })))
    works.value = workResults.flatMap(({ collection, items }) => items.flatMap((work) => {
      const workLanguage = languages.find((candidate) => work.available_languages.includes(candidate))
      return workLanguage ? [{ ...work, language: workLanguage, collection }] : []
    }))
    message.value = prayers.value.length || works.value.length ? '' : text.value.prayers.empty
  } catch (error) {
    const saved = await repository.listPrayers()
    prayers.value = saved
      .map(({ data }) => ({ ...data, excerpt: data.intro ?? contentExcerpt(data.body) }))
      .filter((prayer) => languages.includes(prayer.language_code))
      .filter(matchesPrayerSettings)
    message.value = saved.length
      ? text.value.prayers.offline
      : error instanceof Error ? error.message : text.value.prayers.unavailable
  }
})

function matchesPrayerSettings(prayer: PrayerSummary): boolean {
  const settings = profile.configuration?.prayers
  if (!settings || settings.prayerBook) return true
  return (settings.morning && isMorning(prayer)) || (settings.evening && isEvening(prayer))
}

function rulePriority(prayer: PrayerSummary): number {
  const settings = profile.configuration?.prayers
  if (settings?.morning && isMorning(prayer)) return 0
  if (settings?.evening && isEvening(prayer)) return 1
  return 2
}

function ruleLabel(prayer: PrayerSummary): string | undefined {
  const priority = rulePriority(prayer)
  return priority === 0 ? text.value.prayers.morning : priority === 1 ? text.value.prayers.evening : undefined
}

function collectionLabel(collection: WorkCard['collection']): string {
  return text.value.prayers[collection]
}

function isMorning(prayer: PrayerSummary): boolean {
  return prayer.category === 'morning' || /утрен|morgen/i.test(prayer.title)
}

function isEvening(prayer: PrayerSummary): boolean {
  return prayer.category === 'evening' || /(сон грядущим|вечер|abend|nacht)/i.test(prayer.title)
}

function contentExcerpt(value: string): string {
  return value.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120)
}
</script>

<template>
  <MobileShell>
    <section class="reader-heading">
      <span class="card-icon"><img src="/app-icons/prayers.png" alt="" /></span>
      <span><p class="eyebrow dark-eyebrow">{{ text.prayers.eyebrow }}</p><h1>{{ text.prayers.title }}</h1></span>
    </section>
    <p v-if="message" class="status" role="status">{{ message }}</p>
    <section v-if="prayers.length || works.length" class="content-catalog">
      <RouterLink v-for="prayer in displayPrayers" :key="`prayer-${prayer.id}`" class="content-card" :to="`/prayers/${prayer.id}`">
        <span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span>
        <span><em>{{ ruleLabel(prayer) ?? text.prayers.prayerBook }} · {{ prayer.language_code.toUpperCase() }}</em><strong>{{ prayer.title }}</strong><small>{{ prayer.excerpt }}</small></span>
        <span aria-hidden="true">→</span>
      </RouterLink>
      <RouterLink v-for="work in works" :key="`${work.collection}-${work.slug}-${work.language}`" class="content-card" :to="`/liturgical/${work.slug}/${work.language}`">
        <span class="module-icon"><img src="/app-icons/prayers.png" alt="" /></span>
        <span><em>{{ collectionLabel(work.collection) }} · {{ work.language.toUpperCase() }}</em><strong>{{ work.title }}</strong><small>{{ work.editions.find((edition) => edition.language === work.language)?.title }}</small></span>
        <span aria-hidden="true">→</span>
      </RouterLink>
    </section>
  </MobileShell>
</template>
