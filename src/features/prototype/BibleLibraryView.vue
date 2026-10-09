<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '@/components/MobileShell.vue'
import BibleLibrary from './BibleLibrary.vue'
import { useI18n } from '@/i18n'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
const router = useRouter()
const route = useRoute()
const { language } = useI18n()
const copy = computed(() => bibleCatalogMessages[language.value])
</script>
<template>
  <MobileShell back-to="/reader">
    <h1>{{ copy.bible_library_title }}</h1>
    <BibleLibrary :initial-tab="route.query.tab === 'catalog' ? 'catalog' : undefined" @read="router.push({ path: '/reader', query: { translation: $event } })" />
  </MobileShell>
</template>
