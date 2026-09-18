<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { ru } from '@/i18n/ru'
import { useProfileStore } from '@/stores/profileStore'

const router = useRouter()
const profile = useProfileStore()
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
      <p class="eyebrow">{{ ru.today.eyebrow }}</p>
      <h1>{{ ru.today.title }}</h1>
      <p>{{ ru.today.intro }}</p>
    </section>

    <section class="today-section">
      <header class="section-heading-row">
        <div><small>{{ ru.today.profileLabel }}</small><h2>{{ ru.today.yourApp }}</h2></div>
        <RouterLink to="/setup/manual?edit=1">{{ ru.today.customize }}</RouterLink>
      </header>

      <div class="module-list">
        <RouterLink v-if="sections.includes('bible')" class="module-card available" to="/reader">
          <span class="module-icon"><img :src="ru.sections.bible.icon" alt="" /></span>
          <span><strong>{{ ru.sections.bible.title }}</strong><small>{{ ru.today.bibleAction }}</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('prayers')" class="module-card available" to="/prayers">
          <span class="module-icon"><img :src="ru.sections.prayers.icon" alt="" /></span>
          <span><strong>{{ ru.sections.prayers.title }}</strong><small>Молитвослов и сохранённые правила</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink v-if="sections.includes('calendar')" class="module-card available" to="/calendar">
          <span class="module-icon"><img :src="ru.sections.calendar.icon" alt="" /></span>
          <span><strong>{{ ru.sections.calendar.title }}</strong><small>Память, пост и чтения дня</small></span>
          <span aria-hidden="true">→</span>
        </RouterLink>
        <div v-if="sections.includes('study')" class="module-card">
          <span class="module-icon"><img :src="ru.sections.study.icon" alt="" /></span>
          <span><strong>{{ ru.sections.study.title }}</strong><small>{{ ru.today.configured }}</small></span>
          <span class="module-status">{{ ru.today.nextStage }}</span>
        </div>
      </div>
    </section>

    <section class="offline-note">
      <img src="/icons/bookmarks.png" alt="" />
      <span><strong>{{ ru.today.localTitle }}</strong><small>{{ ru.today.localDescription }}</small></span>
    </section>
  </MobileShell>
</template>
