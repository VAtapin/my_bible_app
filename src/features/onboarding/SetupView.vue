<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { ru } from '@/i18n/ru'
import {
  createConfiguration,
  sectionsForPreset,
  type AppSectionId,
  type CalendarLevel,
  type PresetId,
  type SetupMode,
} from '@/profile/configuration'
import { useProfileStore } from '@/stores/profileStore'

const props = defineProps<{ mode: SetupMode }>()
const route = useRoute()
const router = useRouter()
const profile = useProfileStore()

const step = ref<'preset' | 'details' | 'summary'>(props.mode === 'quick' ? 'preset' : 'details')
const preset = ref<PresetId>('daily')
const sections = ref<AppSectionId[]>(props.mode === 'quick' ? sectionsForPreset('daily') : ['bible'])
const translationCode = ref('BQ_RUSSIAN_RST_STRONG')
const morningPrayer = ref(true)
const eveningPrayer = ref(true)
const prayerBook = ref(true)
const calendarLevel = ref<CalendarLevel>('major')
const notificationsEnabled = ref(false)
const notificationTime = ref('08:00')
const message = ref('')

const editing = computed(() => route.query.edit === '1')
const selectedSectionLabels = computed(() => sections.value.map((id) => ru.sections[id].title))

onMounted(() => {
  const existing = profile.load()
  if (!existing || !editing.value) {
    return
  }

  preset.value = existing.preset ?? 'daily'
  sections.value = [...existing.sections]
  translationCode.value = existing.bible.translationCode
  morningPrayer.value = existing.prayers.morning
  eveningPrayer.value = existing.prayers.evening
  prayerBook.value = existing.prayers.prayerBook
  calendarLevel.value = existing.calendar.level
  notificationsEnabled.value = existing.notifications.enabled
  notificationTime.value = existing.notifications.time
  step.value = 'details'
})

function choosePreset(value: PresetId): void {
  preset.value = value
  sections.value = sectionsForPreset(value)
  step.value = 'details'
}

function toggleSection(section: AppSectionId): void {
  sections.value = sections.value.includes(section)
    ? sections.value.filter((current) => current !== section)
    : [...sections.value, section]
  message.value = ''
}

function showSummary(): void {
  if (sections.value.length === 0) {
    message.value = ru.setup.sectionRequired
    return
  }
  message.value = ''
  step.value = 'summary'
}

function save(): void {
  try {
    const configuration = createConfiguration({
      setupMode: props.mode,
      preset: props.mode === 'quick' ? preset.value : null,
      sections: sections.value,
      translationCode: translationCode.value,
      morningPrayer: morningPrayer.value,
      eveningPrayer: eveningPrayer.value,
      prayerBook: prayerBook.value,
      calendarLevel: calendarLevel.value,
      notificationsEnabled: notificationsEnabled.value,
      notificationTime: notificationTime.value,
    }, profile.configuration)
    profile.save(configuration)
    void router.push('/today')
  } catch (error) {
    message.value = error instanceof Error && error.message === 'sections-required'
      ? ru.setup.sectionRequired
      : ru.unknownError
  }
}
</script>

<template>
  <MobileShell :show-navigation="false" :back-to="editing ? '/today' : '/'">
    <section class="setup-heading">
      <p class="eyebrow dark-eyebrow">{{ mode === 'quick' ? ru.setup.quickEyebrow : ru.setup.manualEyebrow }}</p>
      <h1>{{ editing ? ru.setup.editTitle : ru.setup.title }}</h1>
      <p>{{ ru.setup.intro }}</p>
      <div class="step-indicator" aria-hidden="true">
        <span :class="{ active: step === 'preset' }"></span>
        <span :class="{ active: step === 'details' }"></span>
        <span :class="{ active: step === 'summary' }"></span>
      </div>
    </section>

    <section v-if="step === 'preset'" class="setup-card">
      <h2>{{ ru.setup.presetTitle }}</h2>
      <p class="section-intro">{{ ru.setup.presetIntro }}</p>
      <div class="preset-grid">
        <button v-for="id in (['daily', 'bible', 'prayer', 'calendar'] as PresetId[])" :key="id" type="button" @click="choosePreset(id)">
          <img :src="ru.presets[id].icon" alt="" />
          <span><strong>{{ ru.presets[id].title }}</strong><small>{{ ru.presets[id].description }}</small></span>
        </button>
      </div>
    </section>

    <section v-else-if="step === 'details'" class="setup-card">
      <h2>{{ ru.setup.sectionsTitle }}</h2>
      <p class="section-intro">{{ ru.setup.sectionsIntro }}</p>
      <div class="section-selector">
        <button
          v-for="id in (['bible', 'prayers', 'calendar', 'study'] as AppSectionId[])"
          :key="id"
          type="button"
          :class="{ selected: sections.includes(id) }"
          :aria-pressed="sections.includes(id)"
          @click="toggleSection(id)"
        >
          <img :src="ru.sections[id].icon" alt="" />
          <span><strong>{{ ru.sections[id].title }}</strong><small>{{ ru.sections[id].description }}</small></span>
          <span class="selection-mark" aria-hidden="true">{{ sections.includes(id) ? '✓' : '+' }}</span>
        </button>
      </div>

      <div v-if="sections.includes('bible')" class="option-group">
        <h3>{{ ru.setup.translationTitle }}</h3>
        <label class="option-row radio-row">
          <input v-model="translationCode" type="radio" value="BQ_RUSSIAN_RST_STRONG" />
          <span><strong>{{ ru.translationName }}</strong><small>{{ ru.setup.translationDescription }}</small></span>
        </label>
      </div>

      <div v-if="sections.includes('prayers')" class="option-group">
        <h3>{{ ru.setup.prayersTitle }}</h3>
        <label class="toggle-row"><span>{{ ru.setup.morningPrayer }}</span><input v-model="morningPrayer" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ ru.setup.eveningPrayer }}</span><input v-model="eveningPrayer" type="checkbox" /></label>
        <label class="toggle-row"><span>{{ ru.setup.prayerBook }}</span><input v-model="prayerBook" type="checkbox" /></label>
      </div>

      <div v-if="sections.includes('calendar')" class="option-group">
        <h3>{{ ru.setup.calendarTitle }}</h3>
        <label class="option-row radio-row">
          <input v-model="calendarLevel" type="radio" value="major" />
          <span>{{ ru.setup.calendarMajor }}</span>
        </label>
        <label class="option-row radio-row">
          <input v-model="calendarLevel" type="radio" value="all" />
          <span>{{ ru.setup.calendarAll }}</span>
        </label>
      </div>

      <div class="option-group">
        <h3>{{ ru.setup.notificationsTitle }}</h3>
        <label class="toggle-row">
          <span><strong>{{ ru.setup.notificationsToggle }}</strong><small>{{ ru.setup.notificationsHint }}</small></span>
          <input v-model="notificationsEnabled" type="checkbox" />
        </label>
        <label v-if="notificationsEnabled" class="time-row">
          <span>{{ ru.setup.notificationTime }}</span>
          <input v-model="notificationTime" type="time" />
        </label>
      </div>

      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <button class="primary-action" type="button" @click="showSummary">{{ ru.setup.continue }}</button>
    </section>

    <section v-else class="setup-card summary-card">
      <div class="summary-mark" aria-hidden="true">✓</div>
      <h2>{{ ru.setup.summaryTitle }}</h2>
      <p class="section-intro">{{ ru.setup.summaryIntro }}</p>
      <dl class="summary-list">
        <div><dt>{{ ru.setup.summarySections }}</dt><dd>{{ selectedSectionLabels.join(', ') }}</dd></div>
        <div v-if="sections.includes('bible')"><dt>{{ ru.translation }}</dt><dd>{{ ru.translationName }}</dd></div>
        <div><dt>{{ ru.setup.summaryNotifications }}</dt><dd>{{ notificationsEnabled ? notificationTime : ru.setup.disabled }}</dd></div>
      </dl>
      <button class="primary-action" type="button" @click="save">{{ editing ? ru.setup.save : ru.setup.create }}</button>
      <button class="text-action" type="button" @click="step = 'details'">{{ ru.setup.change }}</button>
    </section>
  </MobileShell>
</template>
