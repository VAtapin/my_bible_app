<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { useProfileStore } from '@/stores/profileStore'
import { interfaceLanguageIds, interfaceLanguageNames, languageFromPath } from '@/i18n/locale'
import WelcomeCalendar from './WelcomeCalendar.vue'

const router = useRouter()
const route = useRoute()
const profile = useProfileStore()
const { language, messages: text, setLanguage } = useI18n()
watch(() => route.path, (path) => { const value = languageFromPath(path); if (value) setLanguage(value) }, { immediate: true })

onMounted(() => {
  if (profile.load()) {
    void router.replace('/today')
  }
})
</script>

<template>
  <MobileShell class="welcome-screen" :show-header="false" :show-navigation="false">
    <div class="welcome-languages" role="group" :aria-label="text.setup.interfaceLanguageTitle">
      <RouterLink v-for="code in interfaceLanguageIds" :key="code" :to="`/${code}`" :lang="code" :aria-current="language === code ? 'true' : undefined" :class="{ selected: language === code }" @click="setLanguage(code)">{{ interfaceLanguageNames[code] }}</RouterLink>
    </div>
    <section class="welcome-hero">
      <div class="welcome-picture" aria-hidden="true"><img src="/brand/welcome-church.png" alt="" /></div>
      <h1>{{ text.welcome.title }}</h1>
      <p>{{ text.welcome.intro }}</p>
    </section>

    <section class="welcome-actions" :aria-label="text.welcome.startLabel">
      <RouterLink class="choice-card welcome-choice primary-choice" to="/setup/quick">
        <svg class="welcome-choice-icon quick-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m13 2-8 12h6l-1 8 9-13h-7l1-7Z" /></svg>
        <span>
          <strong>{{ text.welcome.quickTitle }}</strong>
          <small>{{ text.welcome.quickDescription }}</small>
        </span>
        <span class="welcome-chevron" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink class="choice-card welcome-choice" to="/setup/manual">
        <svg class="welcome-choice-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m10 3 4 0 .5 3 2 .9 2.5-1.4 2 3.5-2.4 1.8v2.4l2.4 1.8-2 3.5-2.5-1.4-2 .9-.5 3h-4l-.5-3-2-.9L5 18.5l-2-3.5 2.4-1.8v-2.4L3 9l2-3.5 2.5 1.4 2-.9.5-3Z" /><circle cx="12" cy="12" r="3" /></svg>
        <span>
          <strong>{{ text.welcome.manualTitle }}</strong>
          <small>{{ text.welcome.manualDescription }}</small>
        </span>
        <span class="welcome-chevron" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink class="choice-card welcome-choice restore-choice" to="/restore">
        <svg class="welcome-choice-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M5 17v4h14v-4" /></svg>
        <span><strong>{{ text.welcome.restore }}</strong><small>{{ text.welcome.restoreDescription }}</small></span>
        <span class="welcome-chevron" aria-hidden="true">›</span>
      </RouterLink>
    </section>

    <blockquote class="welcome-quote"><p>{{ text.welcome.verse }}</p><cite>{{ text.welcome.verseReference }}</cite></blockquote>
    <WelcomeCalendar />
    <RouterLink class="privacy-link" to="/privacy">{{ text.welcome.privacy }}</RouterLink>
  </MobileShell>
</template>
