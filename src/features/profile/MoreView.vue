<script setup lang="ts">
import { RouterLink } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { computed } from 'vue'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { studyMessages } from '@/i18n/study'
import { dictionaryMessages } from '@/i18n/dictionaries'
import { personalStudyMessages } from '@/i18n/personalStudy'
import { studyPackagesMessages } from '@/i18n/studyPackages'
import {readerHelpMessages} from '@/i18n/readerHelp'
import {geographyMessages} from '@/i18n/geography'
import { useAppearance, type AppearanceTheme } from '@/profile/appearance'
const { language, messages: text } = useI18n()
const catalogue = computed(() => bibleCatalogMessages[language.value])
const appearance = useAppearance()
const themes: AppearanceTheme[] = ['classic', 'modern', 'warm']
const items = [
  { route: '/setup/manual?edit=1', title: 'customize', description: 'setupDescription', icon: 'setup' },
  { route: '/notifications', title: 'notificationsTitle', description: 'notificationsDescription', icon: 'calendar' },
  { route: '/profile', title: 'profileTitle', description: 'profileDescription', icon: 'bookmarks' },
  { route: '/storage', title: 'storageTitle', description: 'storageDescription', icon: 'library' },
  { route: '/diagnostics', title: 'diagnosticsTitle', description: 'diagnosticsDescription', icon: 'setup' },
] as const
</script>
<template>
  <MobileShell back-to="/today">
    <h1 class="compact-page-title">{{ text.appearance.settingsTitle }}</h1>
    <div class="module-list">
      <RouterLink to="/help" class="module-card available"><span class="module-icon"><img src="/app-icons/setup.png" alt="" /></span><strong>{{readerHelpMessages[language].title}}</strong></RouterLink>
      <RouterLink to="/atlas" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{dictionaryMessages[language].maps}}</strong></RouterLink>
      <RouterLink to="/study-downloads" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{ studyPackagesMessages[language].title }}</strong></RouterLink>
      <RouterLink to="/dictionaries" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{ dictionaryMessages[language].dictionaries }}</strong></RouterLink>
      <RouterLink to="/storage?tab=study" class="module-card available"><span class="module-icon"><img src="/app-icons/bookmarks.png" alt="" /></span><strong>{{ personalStudyMessages[language].library }}</strong></RouterLink>
      <RouterLink to="/search" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{ text.readerActions.search }}</strong></RouterLink>
      <RouterLink to="/books" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{ studyMessages[language].books }}</strong></RouterLink>
      <RouterLink to="/bibles" class="module-card available"><span class="module-icon"><img src="/app-icons/library.png" alt="" /></span><strong>{{ catalogue.bible_library_title }} · {{ catalogue.catalog_add }}</strong></RouterLink>
      <RouterLink to="/storage?tab=bookmarks" class="module-card available"><span class="module-icon"><img src="/app-icons/bookmarks.png" alt="" /></span><strong>{{ text.readerActions.myBookmarks }}</strong><span aria-hidden="true">→</span></RouterLink>
      <RouterLink to="/storage?tab=notes" class="module-card available"><span class="module-icon"><img src="/app-icons/setup.png" alt="" /></span><strong>{{ text.readerActions.myNotes }}</strong><span aria-hidden="true">→</span></RouterLink>
      <RouterLink v-for="item in items" :key="item.route" :to="item.route" class="module-card available">
        <span class="module-icon"><img :src="`/app-icons/${item.icon}.png`" alt="" /></span>
        <span><strong>{{ text.today[item.title] }}</strong><small>{{ text.today[item.description] }}</small></span><span aria-hidden="true">→</span>
      </RouterLink>
    </div>
    <fieldset class="appearance-options">
      <legend>{{ text.appearance.title }}</legend>
      <div class="appearance-choices">
      <label v-for="theme in themes" :key="theme" :class="['appearance-choice', theme, { selected: appearance.theme.value === theme }]">
        <input type="radio" name="appearance" :value="theme" :aria-label="text.appearance[theme]" :aria-describedby="`appearance-description-${theme}`" :checked="appearance.theme.value === theme" @change="appearance.setTheme(theme)" />
        <span class="theme-swatch" aria-hidden="true"><i></i><i></i><i></i></span>
        <span class="appearance-caption"><strong>{{ text.appearance[theme] }}</strong><small :id="`appearance-description-${theme}`" class="visually-hidden">{{ text.appearance[`${theme}Description`] }}</small></span>
      </label>
      </div>
    </fieldset>
    <a href="/licenses/Snowball-JS-BSD.txt" target="_blank" rel="noopener">Snowball · BSD</a>
  </MobileShell>
</template>
