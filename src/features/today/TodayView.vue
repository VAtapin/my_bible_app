<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'

const router = useRouter()
const profile = useProfileStore()
const { messages: text } = useI18n()
const sections = computed(() => profile.configuration?.sections ?? [])

onMounted(() => {
  if (!profile.load()) {
    void router.replace('/')
  }
})
</script>

<template>
  <MobileShell>
    <section class="today-hero">
      <p class="eyebrow">{{ text.today.eyebrow }}</p>
      <h1>{{ text.today.title }}</h1>
      <p>{{ text.today.intro }}</p>
    </section>

    <section class="today-section">
      <header class="section-heading-row">
        <div><small>{{ text.today.profileLabel }}</small><h2>{{ text.today.yourApp }}</h2></div>
        <RouterLink to="/setup/manual?edit=1">{{ text.today.customize }}</RouterLink>
      </header>

      <div class="module-list">
        <RouterLink v-if="sections.includes('bible')" class="module-card available" to="/reader">
          <span class="module-icon"><img :src="text.sections.bible.icon" alt="" /></span>
          <span><strong>{{ text.sections.bible.title }}</strong><small>{{ text.today.bibleAction }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('prayers')" class="module-card available" to="/prayers">
          <span class="module-icon"><img :src="text.sections.prayers.icon" alt="" /></span>
          <span><strong>{{ text.sections.prayers.title }}</strong><small>{{ text.today.prayersDescription }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('calendar')" class="module-card available" to="/calendar">
          <span class="module-icon"><img :src="text.sections.calendar.icon" alt="" /></span>
          <span><strong>{{ text.sections.calendar.title }}</strong><small>{{ text.today.calendarDescription }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <div v-if="sections.includes('study')" class="module-card">
          <span class="module-icon"><img :src="text.sections.study.icon" alt="" /></span>
          <span><strong>{{ text.sections.study.title }}</strong><small>{{ text.today.configured }}</small></span>
          <span class="module-status">{{ text.today.nextStage }}</span>
        </div>
        <RouterLink class="module-card available" to="/notifications">
          <span class="module-icon"><img src="/app-icons/calendar.png" alt="" /></span>
          <span><strong>{{ text.today.notificationsTitle }}</strong><small>{{ text.today.notificationsDescription }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink class="module-card available" to="/profile">
          <span class="module-icon"><img src="/app-icons/bookmarks.png" alt="" /></span>
          <span><strong>{{ text.today.profileTitle }}</strong><small>{{ text.today.profileDescription }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink class="module-card available" to="/diagnostics">
          <span class="module-icon"><img src="/app-icons/setup.png" alt="" /></span>
          <span><strong>{{ text.today.diagnosticsTitle }}</strong><small>{{ text.today.diagnosticsDescription }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
      </div>
    </section>

    <section class="offline-note">
      <img src="/app-icons/bookmarks.png" alt="" />
      <span><strong>{{ text.today.localTitle }}</strong><small>{{ text.today.localDescription }}</small></span>
    </section>
  </MobileShell>
</template>
