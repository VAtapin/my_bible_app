<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { CalendarDay } from '@/api/contracts'
import { bibleApi } from '@/api'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n, formatMessage } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { defaultCalendarHome, educationSettings } from '@/profile/configuration'
import { calendarDateInTimeZone, formatTodayDate } from '@/services/calendarDates'
import { createDailyContentService } from '@/services/dailyContentService'
import { createIndexedDbDailyContentRepository } from '@/offline/indexedDbDailyContentRepository'
import { createChapterService } from '@/services/chapterService'
import { loadRandomVerse } from '@/services/randomVerse'
import { loadGospelExcerpt } from '@/services/dailyReading'
import { calendarReadingLink } from '@/services/calendarPresentation'
import { useAppearance } from '@/profile/appearance'
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
const calendarHome = computed(() => profile.configuration?.calendar.home ?? defaultCalendarHome())
const now = ref(new Date())
const date = computed(() => calendarDateInTimeZone(now.value))
const day = ref<CalendarDay>()
const calendarLoading = ref(false)
const calendarFailed = ref(false)
const calendarOffline = ref(false)
const dailyService = createDailyContentService(bibleApi, createIndexedDbDailyContentRepository())
const dateLabel = computed(() => formatTodayDate(date.value, day.value?.old_style_date, language.value === 'de' ? 'de-DE' : 'ru-RU'))
const verse = ref<Awaited<ReturnType<typeof loadRandomVerse>>>()
const gospel = ref<Awaited<ReturnType<typeof loadGospelExcerpt>>>()
const chapters = createChapterService(bibleApi, createIndexedDbChapterRepository())
const verseTranslation = computed(() => profile.configuration?.bible.translationCodes.find((code) => code.includes(language.value === 'de' ? 'GERMAN' : 'RUSSIAN'))
  ?? (language.value === 'de' ? 'BQ_GERMAN_ELBERFELD_STRONG' : 'BQ_RUSSIAN_RST_STRONG'))
const appearance = useAppearance()
const icon = computed(() => day.value?.icons?.find((item) => item.image_url))
let calendarGeneration = 0
watch(() => [date.value, profile.configuration?.calendar.languageCode], async () => {
  if (!profile.configuration) return
  const generation = ++calendarGeneration
  day.value = undefined
  gospel.value = undefined
  calendarLoading.value = true
  calendarFailed.value = false
  try {
    const result = await dailyService.openCalendarDay(date.value, profile.configuration.calendar.languageCode)
    if (generation === calendarGeneration) { day.value = result.data; calendarOffline.value = result.offline }
    if (profile.configuration?.sections.includes('calendar') && calendarHome.value.readings) {
      void loadGospelExcerpt(chapters, result.data.readings, verseTranslation.value).then((excerpt) => {
        if (generation === calendarGeneration) gospel.value = excerpt
      }).catch(() => { if (generation === calendarGeneration) gospel.value = undefined })
    }
  } catch { if (generation === calendarGeneration) calendarFailed.value = true }
  finally { if (generation === calendarGeneration) calendarLoading.value = false }
}, { immediate: true })
const readingLocation = ref<ReadingLocation>()
const readingLabel = ref('')
const readerLink = computed(() => readingLocation.value ? { path: '/reader', query: { translation: readingLocation.value.translationCode, book: readingLocation.value.bookSlug, chapter: String(readingLocation.value.chapter) } } : '/reader')
const learnedCount = computed(() => azbuka.profile?.learnedLetterIds.length ?? 0)
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
  tasks.push(loadRandomVerse(chapters, verseTranslation.value).then((value) => { verse.value = value }))
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
    <header class="today-hero">
      <p class="eyebrow">{{ text.today.eyebrow }}</p>
      <h1><span class="civil-date">{{ dateLabel.split(' (')[0] }}</span><span v-if="dateLabel.includes(' (')" class="old-style-date">{{ ` (${dateLabel.split(' (')[1]}` }}</span></h1>
      <RouterLink v-if="hasAzbuka" class="today-clock-link" to="/education/azbuka/numbers">
        <SlavonicClock compact :show-label="false" :label="text.education.clockTitle" />
      </RouterLink>
      <RouterLink v-if="verse" class="today-verse" :to="verse.route"><blockquote>«{{ verse.text }}»</blockquote><cite>{{ verse.reference }}</cite></RouterLink>
    </header>
    <article v-if="appearance.theme.value === 'modern' && sections.includes('calendar') && day && (icon || (calendarHome.readings && day.readings.length))" class="icon-day-card">
      <img v-if="icon" :src="icon.image_url!" :alt="icon.title" @error="($event.target as HTMLImageElement).hidden = true" />
      <span>
        <strong v-if="icon">{{ icon.title }}</strong><small v-if="icon?.credit">{{ icon.credit }}</small>
        <template v-if="calendarHome.readings && gospel"><small>{{ gospel.reference }}</small><blockquote>«{{ gospel.text }}»</blockquote><RouterLink :to="gospel.route">{{ text.calendar.open }} →</RouterLink></template>
        <template v-else-if="calendarHome.readings"><RouterLink v-for="reading in day.readings.slice(0, 2)" :key="reading.id" :to="calendarReadingLink(reading) ?? '/calendar'">{{ reading.display_ref || reading.title }} →</RouterLink></template>
      </span>
    </article>

    <section class="today-section">
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
        <RouterLink v-if="hasAzbuka" class="module-card available" to="/education/azbuka">
          <span class="module-icon"><img src="/app-icons/library.png" alt="" /></span>
          <span>
            <strong>{{ text.education.apps.azbuka.title }}</strong>
            <small v-if="education.showProgress">{{ formatMessage(text.education.progress, { count: learnedCount, total: letters.length }) }}</small>
            <small v-if="education.showProgress && azbuka.profile">{{ formatMessage(text.education.goal, { count: goalCount, total: azbuka.profile.dailyGoal }) }}</small>
          </span>
          <span class="module-action">{{ text.today.continue }} →</span>
        </RouterLink>
        <article v-if="sections.includes('calendar') && profile.configuration" class="calendar-module">
          <RouterLink class="module-card available" to="/calendar">
            <span class="module-icon"><img :src="text.sections.calendar.icon" alt="" /></span>
            <span><strong>{{ text.sections.calendar.title }}</strong></span><span aria-hidden="true">→</span>
          </RouterLink>
          <CalendarSummary :day="day" :loading="calendarLoading" :failed="calendarFailed" :offline="calendarOffline" :settings="profile.configuration.calendar" />
        </article>
      </div>
    </section>
  </MobileShell>
</template>
