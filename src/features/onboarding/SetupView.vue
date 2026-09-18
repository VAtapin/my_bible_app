<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { bibleApi } from '@/api'
import type { TranslationSummary } from '@/api/contracts'
import { useI18n } from '@/i18n'
import type { InterfaceLanguage } from '@/i18n/locale'
import {
  createConfiguration,
  sectionsForPreset,
  type AppSectionId,
  type CalendarLevel,
  type PresetId,
  type SetupMode,
} from '@/profile/configuration'
import { useProfileStore } from '@/stores/profileStore'
import { recordProductMetric } from '@/diagnostics/productDiagnostics'
import { initialSetupStep, stepAfterPreset, stepBeforeSummary } from './setupFlow'

const props = defineProps<{ mode: SetupMode }>()
const route = useRoute()
const router = useRouter()
const profile = useProfileStore()
const { language, messages: text, setLanguage } = useI18n()

const step = ref(initialSetupStep(props.mode))
const interfaceLanguage = ref<InterfaceLanguage>(language.value)
const preset = ref<PresetId>('daily')
const sections = ref<AppSectionId[]>(props.mode === 'quick' ? sectionsForPreset('daily') : ['bible'])
const translations = ref<TranslationSummary[]>([])
const translationsLoading = ref(true)
const translationCodes = ref<string[]>([defaultTranslationCode(interfaceLanguage.value)])
const morningPrayer = ref(true)
const eveningPrayer = ref(true)
const prayerBook = ref(true)
const akathists = ref(false)
const canons = ref(false)
const horologion = ref(false)
const prayerLanguageCodes = ref<string[]>([interfaceLanguage.value])
const calendarLevel = ref<CalendarLevel>('major')
const notificationsEnabled = ref(false)
const notificationTime = ref('08:00')
const message = ref('')

const editing = computed(() => route.query.edit === '1')
const selectedSectionLabels = computed(() => sections.value.map((id) => text.value.sections[id].title))
const selectedTranslationNames = computed(() => translationCodes.value.map((code) => (
  translations.value.find((item) => item.code === code)?.name ?? code
)))
const translationGroups = computed(() => {
  const groups = new Map<string, { code: string; name: string; translations: TranslationSummary[] }>()
  for (const translation of translations.value) {
    const group = groups.get(translation.language.code) ?? {
      code: translation.language.code,
      name: translation.language.name,
      translations: [],
    }
    group.translations.push(translation)
    groups.set(translation.language.code, group)
  }
  return [...groups.values()]
})
const selectedPrayerLabels = computed(() => [
  morningPrayer.value ? text.value.setup.morningPrayer : '',
  eveningPrayer.value ? text.value.setup.eveningPrayer : '',
  prayerBook.value ? text.value.setup.prayerBook : '',
  akathists.value ? text.value.setup.akathists : '',
  canons.value ? text.value.setup.canons : '',
  horologion.value ? text.value.setup.horologion : '',
].filter(Boolean))

onMounted(async () => {
  const existing = profile.load()
  if (!editing.value) recordProductMetric('constructor_opened')
  if (!editing.value) setLanguage(interfaceLanguage.value)
  if (existing && editing.value) {
    interfaceLanguage.value = existing.interfaceLanguage
    preset.value = existing.preset ?? 'daily'
    sections.value = [...existing.sections]
    translationCodes.value = [...existing.bible.translationCodes]
    morningPrayer.value = existing.prayers.morning
    eveningPrayer.value = existing.prayers.evening
    prayerBook.value = existing.prayers.prayerBook
    akathists.value = existing.prayers.akathists
    canons.value = existing.prayers.canons
    horologion.value = existing.prayers.horologion
    prayerLanguageCodes.value = [...existing.prayers.languageCodes]
    calendarLevel.value = existing.calendar.level
    notificationsEnabled.value = existing.notifications.enabled
    notificationTime.value = existing.notifications.time
    step.value = 'details'
  }

  try {
    translations.value = await bibleApi.getTranslations()
    ensureTranslationSelection()
  } catch {
    message.value = text.value.setup.translationsUnavailable
  } finally {
    translationsLoading.value = false
  }
})

function changeInterfaceLanguage(value: InterfaceLanguage): void {
  const previousLanguage = interfaceLanguage.value
  interfaceLanguage.value = value
  setLanguage(value)
  if (props.mode === 'quick' && !editing.value) {
    const previousDefault = preferredTranslationCode(previousLanguage)
    const nextDefault = preferredTranslationCode(value)
    const additionalTranslations = translationCodes.value.filter((code) => code !== previousDefault && code !== nextDefault)
    translationCodes.value = [nextDefault, ...additionalTranslations]
    prayerLanguageCodes.value = [value]
  }
}

function choosePreset(value: PresetId): void {
  preset.value = value
  sections.value = sectionsForPreset(value)
  step.value = stepAfterPreset(props.mode)
}

function toggleSection(section: AppSectionId): void {
  sections.value = sections.value.includes(section)
    ? sections.value.filter((current) => current !== section)
    : [...sections.value, section]
  message.value = ''
}

function toggleTranslation(code: string): void {
  translationCodes.value = translationCodes.value.includes(code)
    ? translationCodes.value.filter((current) => current !== code)
    : [...translationCodes.value, code]
}

function setPrimaryTranslation(code: string): void {
  translationCodes.value = [code, ...translationCodes.value.filter((current) => current !== code)]
}

function togglePrayerLanguage(code: string): void {
  prayerLanguageCodes.value = prayerLanguageCodes.value.includes(code)
    ? prayerLanguageCodes.value.filter((current) => current !== code)
    : [...prayerLanguageCodes.value, code]
}

function showSummary(): void {
  if (sections.value.length === 0) {
    message.value = text.value.setup.sectionRequired
    return
  }
  message.value = ''
  step.value = 'summary'
}

function save(): void {
  try {
    const configuration = createConfiguration({
      interfaceLanguage: interfaceLanguage.value,
      setupMode: props.mode,
      preset: props.mode === 'quick' ? preset.value : null,
      sections: sections.value,
      translationCodes: translationCodes.value,
      morningPrayer: morningPrayer.value,
      eveningPrayer: eveningPrayer.value,
      prayerBook: prayerBook.value,
      akathists: akathists.value,
      canons: canons.value,
      horologion: horologion.value,
      prayerLanguageCodes: prayerLanguageCodes.value,
      calendarLevel: calendarLevel.value,
      notificationsEnabled: notificationsEnabled.value,
      notificationTime: notificationTime.value,
    }, profile.configuration)
    profile.save(configuration)
    if (!editing.value) recordProductMetric('constructor_completed')
    void router.push(notificationsEnabled.value ? '/notifications?onboarding=1' : '/today')
  } catch (error) {
    message.value = error instanceof Error && error.message === 'sections-required'
      ? text.value.setup.sectionRequired
      : text.value.unknownError
  }
}

function ensureTranslationSelection(): void {
  const available = new Set(translations.value.map((item) => item.code))
  translationCodes.value = translationCodes.value.filter((code) => available.has(code))
  if (!translationCodes.value.length) translationCodes.value = [preferredTranslationCode(interfaceLanguage.value)]
}

function preferredTranslationCode(value: InterfaceLanguage): string {
  const choices = translations.value.filter((item) => item.language.code === value)
  return (choices.find((item) => item.is_default) ?? choices[0])?.code ?? defaultTranslationCode(value)
}

function defaultTranslationCode(value: InterfaceLanguage): string {
  return value === 'de' ? 'BQ_GERMAN_ELBERFELD_STRONG' : 'BQ_RUSSIAN_RST_STRONG'
}
</script>

<template>
  <MobileShell :show-navigation="false" :back-to="editing ? '/today' : '/'">
    <section class="setup-heading">
      <p class="eyebrow dark-eyebrow">{{ mode === 'quick' ? text.setup.quickEyebrow : text.setup.manualEyebrow }}</p>
      <h1>{{ editing ? text.setup.editTitle : text.setup.title }}</h1>
      <p>{{ text.setup.intro }}</p>
      <div class="step-indicator" aria-hidden="true">
        <span :class="{ active: step === (mode === 'quick' ? 'preset' : 'details') }"></span>
        <span :class="{ active: step === 'summary' }"></span>
      </div>
    </section>

    <section class="setup-card language-card">
      <h2>{{ text.setup.interfaceLanguageTitle }}</h2>
      <p class="section-intro">{{ text.setup.interfaceLanguageIntro }}</p>
      <div class="language-selector" role="group" :aria-label="text.setup.interfaceLanguageTitle">
        <button type="button" :class="{ selected: interfaceLanguage === 'ru' }" :aria-pressed="interfaceLanguage === 'ru'" @click="changeInterfaceLanguage('ru')">{{ text.setup.russian }}</button>
        <button type="button" :class="{ selected: interfaceLanguage === 'de' }" :aria-pressed="interfaceLanguage === 'de'" @click="changeInterfaceLanguage('de')">{{ text.setup.german }}</button>
      </div>
    </section>

    <section v-if="step === 'preset'" class="setup-card">
      <h2>{{ text.setup.presetTitle }}</h2>
      <p class="section-intro">{{ text.setup.presetIntro }}</p>
      <div class="preset-grid">
        <button v-for="id in (['daily', 'bible', 'prayer', 'calendar'] as PresetId[])" :key="id" type="button" @click="choosePreset(id)">
          <img :src="text.presets[id].icon" alt="" />
          <span><strong>{{ text.presets[id].title }}</strong><small>{{ text.presets[id].description }}</small></span>
        </button>
      </div>
    </section>

    <section v-else-if="step === 'details'" class="setup-card">
      <h2>{{ text.setup.sectionsTitle }}</h2>
      <p class="section-intro">{{ text.setup.sectionsIntro }}</p>
      <div class="section-selector">
        <button
          v-for="id in (['bible', 'prayers', 'calendar', 'study'] as AppSectionId[])"
          :key="id"
          type="button"
          :class="{ selected: sections.includes(id) }"
          :aria-pressed="sections.includes(id)"
          @click="toggleSection(id)"
        >
          <img :src="text.sections[id].icon" alt="" />
          <span><strong>{{ text.sections[id].title }}</strong><small>{{ text.sections[id].description }}</small></span>
          <span class="selection-mark" aria-hidden="true">{{ sections.includes(id) ? '✓' : '+' }}</span>
        </button>
      </div>

      <div v-if="sections.includes('bible')" class="option-group">
        <h3>{{ text.setup.translationTitle }}</h3>
        <p class="option-hint">{{ text.setup.translationDescription }}</p>
        <p v-if="translationsLoading" class="option-hint">{{ text.setup.translationsLoading }}</p>
        <details v-for="group in translationGroups" v-else :key="group.code" class="translation-group" :open="group.code === interfaceLanguage">
          <summary>{{ group.name }}</summary>
          <label v-for="translation in group.translations" :key="translation.code" class="toggle-row">
            <span><strong>{{ translation.name }}</strong><small>{{ translation.short_name ?? translation.code }}</small></span>
            <input type="checkbox" :checked="translationCodes.includes(translation.code)" @change="toggleTranslation(translation.code)" />
          </label>
        </details>
      </div>

      <div v-if="sections.includes('prayers')" class="option-group">
        <h3>{{ text.setup.prayersTitle }}</h3>
        <label class="toggle-row"><span>{{ text.setup.morningPrayer }}</span><input v-model="morningPrayer" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ text.setup.eveningPrayer }}</span><input v-model="eveningPrayer" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ text.setup.prayerBook }}</span><input v-model="prayerBook" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ text.setup.akathists }}</span><input v-model="akathists" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ text.setup.canons }}</span><input v-model="canons" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ text.setup.horologion }}</span><input v-model="horologion" type="checkbox" /></label>
        <h3 class="suboption-title">{{ text.setup.prayerLanguagesTitle }}</h3>
        <div class="language-selector compact" role="group" :aria-label="text.setup.prayerLanguagesTitle">
          <button type="button" :class="{ selected: prayerLanguageCodes.includes('ru') }" :aria-pressed="prayerLanguageCodes.includes('ru')" @click="togglePrayerLanguage('ru')">{{ text.setup.russian }}</button>
          <button type="button" :class="{ selected: prayerLanguageCodes.includes('de') }" :aria-pressed="prayerLanguageCodes.includes('de')" @click="togglePrayerLanguage('de')">{{ text.setup.german }}</button>
          <button type="button" :class="{ selected: prayerLanguageCodes.includes('cu-civil') }" :aria-pressed="prayerLanguageCodes.includes('cu-civil')" @click="togglePrayerLanguage('cu-civil')">{{ text.setup.churchSlavonic }}</button>
        </div>
      </div>

      <div v-if="sections.includes('calendar')" class="option-group">
        <h3>{{ text.setup.calendarTitle }}</h3>
        <label class="option-row radio-row">
          <input v-model="calendarLevel" type="radio" value="major" />
          <span>{{ text.setup.calendarMajor }}</span>
        </label>
        <label class="option-row radio-row">
          <input v-model="calendarLevel" type="radio" value="all" />
          <span>{{ text.setup.calendarAll }}</span>
        </label>
      </div>

      <div class="option-group">
        <h3>{{ text.setup.notificationsTitle }}</h3>
        <label class="toggle-row">
          <span><strong>{{ text.setup.notificationsToggle }}</strong><small>{{ text.setup.notificationsHint }}</small></span>
          <input v-model="notificationsEnabled" type="checkbox" />
        </label>
        <label v-if="notificationsEnabled" class="time-row">
          <span>{{ text.setup.notificationTime }}</span>
          <input v-model="notificationTime" type="time" />
        </label>
      </div>

      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <button class="primary-action" type="button" @click="showSummary">{{ text.setup.continue }}</button>
    </section>

    <section v-else class="setup-card summary-card">
      <div class="summary-mark" aria-hidden="true">✓</div>
      <h2>{{ text.setup.summaryTitle }}</h2>
      <p class="section-intro">{{ text.setup.summaryIntro }}</p>
      <dl class="summary-list">
        <div v-if="mode === 'quick'"><dt>{{ text.setup.summaryPreset }}</dt><dd>{{ text.presets[preset].title }}</dd></div>
        <div><dt>{{ text.setup.summaryLanguage }}</dt><dd>{{ interfaceLanguage === 'de' ? text.setup.german : text.setup.russian }}</dd></div>
        <div><dt>{{ text.setup.summarySections }}</dt><dd>{{ selectedSectionLabels.join(', ') }}</dd></div>
        <div v-if="sections.includes('bible')">
          <dt>{{ text.setup.summaryTranslations }}</dt>
          <dd v-if="mode === 'quick'">
            <select :value="translationCodes[0]" @change="setPrimaryTranslation(($event.target as HTMLSelectElement).value)">
              <optgroup v-for="group in translationGroups" :key="group.code" :label="group.name">
                <option v-for="translation in group.translations" :key="translation.code" :value="translation.code">{{ translation.name }}</option>
              </optgroup>
            </select>
            <details class="quick-translation-options">
              <summary>{{ text.setup.addTranslations }}</summary>
              <div v-for="group in translationGroups" :key="group.code" class="quick-translation-group">
                <strong>{{ group.name }}</strong>
                <label v-for="translation in group.translations.filter((item) => item.code !== translationCodes[0])" :key="translation.code">
                  <input type="checkbox" :checked="translationCodes.includes(translation.code)" @change="toggleTranslation(translation.code)" />
                  <span>{{ translation.name }}</span>
                </label>
              </div>
            </details>
          </dd>
          <dd v-else>{{ selectedTranslationNames.join(', ') }}</dd>
        </div>
        <div v-if="sections.includes('prayers')"><dt>{{ text.setup.summaryPrayerContent }}</dt><dd>{{ selectedPrayerLabels.join(', ') }}</dd></div>
        <div><dt>{{ text.setup.summaryNotifications }}</dt><dd>{{ notificationsEnabled ? notificationTime : text.setup.disabled }}</dd></div>
      </dl>
      <button class="primary-action" type="button" @click="save">{{ editing ? text.setup.save : text.setup.create }}</button>
      <button class="text-action" type="button" @click="step = stepBeforeSummary(mode)">
        {{ mode === 'quick' ? text.setup.changePreset : text.setup.change }}
      </button>
    </section>
  </MobileShell>
</template>
