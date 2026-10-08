<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { bibleApi } from '@/api'
import type { LiturgicalWorkVersion } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'
import { normalizePrayerText } from '@/services/prayerContent'
import { prayerLanguageLabel } from '@/services/prayerEditions'

const route = useRoute()
const { messages: text } = useI18n()
const work = ref<LiturgicalWorkVersion>()
const message = ref('')
const visibleBlocks = computed(() => work.value?.blocks.filter((block) => block.text.trim()).map((block) => ({ ...block, text: normalizePrayerText(block.text) })) ?? [])

onMounted(async () => {
  const slug = String(route.params.slug ?? '')
  const workLanguage = String(route.params.language ?? '')
  if (!slug || !workLanguage) {
    message.value = text.value.prayers.notFound
    return
  }
  message.value = text.value.prayers.loadingPrayer
  try {
    work.value = await bibleApi.getLiturgicalVersion(slug, workLanguage, typeof route.query.edition === 'string' ? route.query.edition : undefined)
    message.value = ''
  } catch (error) {
    message.value = error instanceof Error ? error.message : text.value.prayers.openFailed
  }
})
</script>

<template>
  <MobileShell back-to="/prayers">
    <article v-if="work" class="prayer-reading liturgical-reading" :class="{ 'traditional-prayer': work.orthography === 'traditional' && work.language === 'cu', 'civil-prayer': work.language.startsWith('cu') && ['civil', 'civil-accented'].includes(work.orthography) }" :lang="work.language.startsWith('cu') ? 'cu' : work.language">
      <p class="eyebrow dark-eyebrow">{{ prayerLanguageLabel(work.language, text.setup) }}</p>
      <h1>{{ work.title }}</h1>
      <template v-for="block in visibleBlocks" :key="block.id">
        <h2 v-if="block.kind === 'heading'" class="prayer-text-heading">{{ block.text }}</h2>
        <p v-else :class="{ rubric: block.kind === 'rubric' }">{{ block.text }}</p>
      </template>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>
