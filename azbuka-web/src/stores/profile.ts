import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Locale, UserProfile } from '@/domain/types'
import { clearProfile, defaultProfile, loadProfile, saveProfile } from '@/storage/profileRepository'
import { useI18n } from '@/i18n'
import { localDateKey, previousLocalDateKey } from '@/domain/date'

export const useProfileStore = defineStore('profile', () => {
  const profile = ref<UserProfile | null>(null)
  const initialized = ref(false)
  const { setLocale } = useI18n()

  const accuracy = computed(() => {
    if (!profile.value?.answered) return 0
    return Math.round((profile.value.correct / profile.value.answered) * 100)
  })

  const initialize = async () => {
    profile.value = await loadProfile()
    if (profile.value) setLocale(profile.value.locale)
    initialized.value = true
  }

  const persist = async () => {
    if (profile.value) await saveProfile(profile.value)
  }

  const create = async (locale: Locale, dailyGoal: number) => {
    profile.value = { ...defaultProfile(), locale, dailyGoal }
    setLocale(locale)
    await persist()
  }

  const changeLocale = async (locale: Locale) => {
    if (!profile.value) return
    profile.value.locale = locale
    setLocale(locale)
    await persist()
  }

  const changeGoal = async (dailyGoal: number) => {
    if (!profile.value) return
    profile.value.dailyGoal = dailyGoal
    await persist()
  }

  const markLearned = async (letterId: string) => {
    if (!profile.value || profile.value.learnedLetterIds.includes(letterId)) return
    profile.value.learnedLetterIds.push(letterId)
    await persist()
  }

  const recordAnswer = async (isCorrect: boolean) => {
    if (!profile.value) return
    const now = new Date()
    const today = localDateKey(now)
    const yesterday = previousLocalDateKey(now)
    if (profile.value.lastPracticeDate !== today) {
      profile.value.streak = profile.value.lastPracticeDate === yesterday ? profile.value.streak + 1 : 1
    }
    profile.value.lastPracticeDate = today
    if (profile.value.todayDate !== today) {
      profile.value.todayDate = today
      profile.value.todayAnswered = 0
    }
    profile.value.todayAnswered += 1
    profile.value.answered += 1
    if (isCorrect) profile.value.correct += 1
    await persist()
  }

  const reset = async () => {
    const locale = profile.value?.locale ?? 'cu'
    const goal = profile.value?.dailyGoal ?? 10
    await clearProfile()
    profile.value = { ...defaultProfile(), locale, dailyGoal: goal }
    await persist()
  }

  return {
    profile, initialized, accuracy, initialize, create, changeLocale, changeGoal,
    markLearned, recordAnswer, reset
  }
})
