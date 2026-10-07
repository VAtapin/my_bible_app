<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n, formatMessage } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { educationSettings } from '@/profile/configuration'
import { calendarDateInTimeZone } from '@/services/calendarDates'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { chapterKey } from '@/offline/chapterRepository'
import type { ReadingLocation } from '@/offline/libraryRepository'
import CalendarSummary from './CalendarSummary.vue'
import SlavonicClock from '../../../azbuka-web/src/components/SlavonicClock.vue'
import { useProfileStore as useAzbukaProfile } from '../../../azbuka-web/src/stores/profile'
import { letters } from '../../../azbuka-web/src/data/letters'

const router = useRouter()
const profile = useProfileStore()
const azbuka = useAzbukaProfile()
const { language, messages: text } = useI18n()
const sections = computed(() => profile.configuration?.sections ?? [])
const education = computed(() => educationSettings(profile.configuration))
const hasAzbuka = computed(() => education.value.pluginIds.includes('azbuka'))
const now = ref(new Date())
const date = computed(() => calendarDateInTimeZone(now.value))
const dateLabel = computed(() => new Intl.DateTimeFormat(language.value === 'de' ? 'de-DE' : 'ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }).format(now.value))
const readingLocation = ref<ReadingLocation>()
const readingLabel = ref('')
const readerLink = computed(() => readingLocation.value ? { path: '/reader', query: { translation: readingLocation.value.translationCode, book: readingLocation.value.bookSlug, chapter: String(readingLocation.value.chapter) } } : '/reader')
const learnedCount = computed(() => azbuka.profile?.learnedLetterIds.length ?? 0)
const nextLetter = computed(() => letters.find((letter) => !azbuka.profile?.learnedLetterIds.includes(letter.id)) ?? letters[0]!)
const goalCount = computed(() => azbuka.profile?.todayDate === date.value ? azbuka.profile.todayAnswered : 0)
const prayerLabel = computed(() => {
  const settings = profile.configuration?.prayers
  if (now.value.getHours() >= 18 && settings?.evening) return text.value.setup.eveningPrayer
  if (settings?.morning) return text.value.setup.morningPrayer
  if (settings?.evening) return text.value.setup.eveningPrayer
  return text.value.today.prayersDescription
})
let timer: number | undefined
function refreshDate(): void { now.value = new Date() }
onMounted(async () => {
  if (!profile.load()) { await router.replace('/'); return }
  timer = window.setInterval(refreshDate, 1000)
  window.addEventListener('focus', refreshDate)
  const tasks: Promise<unknown>[] = []
  if (hasAzbuka.value) tasks.push(azbuka.initialize())
  if (sections.value.includes('bible')) tasks.push((async () => {
    const location = await createIndexedDbLibraryRepository().getReadingLocation()
    if (!location || !profile.configuration?.bible.translationCodes.includes(location.translationCode)) return
    readingLocation.value = location
    const stored = await createIndexedDbChapterRepository().get(chapterKey(location.translationCode, location.bookSlug, location.chapter))
    readingLabel.value = stored ? `${stored.data.book.name}, ${text.value.reader.chapterLabel} ${location.chapter}` : `${location.bookSlug}, ${text.value.reader.chapterLabel} ${location.chapter}`
  })())
  await Promise.allSettled(tasks)
})
onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
  window.removeEventListener('focus', refreshDate)
})
</script>

<template>
  <MobileShell>
    <header class="today-overview">
      <div><h1>{{ dateLabel }}</h1></div>
      <RouterLink v-if="hasAzbuka && education.showClock" class="today-clock-link" to="/education/azbuka/numbers">
        <SlavonicClock compact :label="text.education.clockTitle" />
      </RouterLink>
    </header>
    <CalendarSummary v-if="sections.includes('calendar') && profile.configuration" :date="date" :settings="profile.configuration.calendar" />

    <section class="today-section">
      <header class="section-heading-row">
        <h2>{{ text.today.yourApp }}</h2>
        <RouterLink to="/setup/manual?edit=1">{{ text.today.customize }}</RouterLink>
      </header>
      <div class="module-list">
        <RouterLink v-if="sections.includes('bible')" class="module-card available" :to="readerLink">
          <span class="module-icon"><img :src="text.sections.bible.icon" alt="" /></span>
          <span><strong>{{ text.sections.bible.title }}</strong><small>{{ readingLabel || text.today.bibleAction }}</small></span>
          <span class="module-action">{{ readingLabel ? text.today.continue : text.calendar.open }} →</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('prayers')" class="module-card available" to="/prayers">
          <span class="module-icon"><img :src="text.sections.prayers.icon" alt="" /></span>
          <span><strong>{{ text.sections.prayers.title }}</strong><small>{{ prayerLabel }}</small></span><span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('calendar')" class="module-card available" to="/calendar">
          <span class="module-icon"><img :src="text.sections.calendar.icon" alt="" /></span>
          <span><strong>{{ text.sections.calendar.title }}</strong><small>{{ text.today.calendarDescription }}</small></span><span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="hasAzbuka" class="module-card available" :to="`/education/azbuka/alphabet/${nextLetter.id}`">
          <span class="module-icon"><img src="/app-icons/library.png" alt="" /></span>
          <span>
            <strong>{{ text.education.apps.azbuka.title }}</strong>
            <small v-if="education.showProgress">{{ formatMessage(text.education.progress, { count: learnedCount, total: letters.length }) }}</small>
            <small v-if="education.showProgress && azbuka.profile">{{ formatMessage(text.education.goal, { count: goalCount, total: azbuka.profile.dailyGoal }) }}</small>
          </span>
          <span class="module-action">{{ text.today.continue }} →</span>
        </RouterLink>
      </div>
    </section>
  </MobileShell>
</template>
