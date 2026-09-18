<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { bibleApi } from '@/api'
import type { LiturgicalWorkVersion } from '@/api/contracts'
import MobileShell from '@/components/MobileShell.vue'
import { useI18n } from '@/i18n'

const route = useRoute()
const { messages: text } = useI18n()
const work = ref<LiturgicalWorkVersion>()
const message = ref('')
const visibleBlocks = computed(() => work.value?.blocks.filter((block) => block.text.trim()) ?? [])

onMounted(async () => {
  const slug = String(route.params.slug ?? '')
  const workLanguage = String(route.params.language ?? '')
  if (!slug || !workLanguage) {
    message.value = text.value.prayers.notFound
    return
  }
  message.value = text.value.prayers.loadingPrayer
  try {
    work.value = await bibleApi.getLiturgicalVersion(slug, workLanguage)
    message.value = ''
  } catch (error) {
    message.value = error instanceof Error ? error.message : text.value.prayers.openFailed
  }
})
</script>

<template>
  <MobileShell back-to="/prayers">
    <article v-if="work" class="prayer-reading liturgical-reading">
      <p class="eyebrow dark-eyebrow">{{ work.language.toUpperCase() }} · {{ work.edition_title }}</p>
      <h1>{{ work.title }}</h1>
      <template v-for="block in visibleBlocks" :key="block.id">
        <h2 v-if="block.kind === 'heading'">{{ block.text }}</h2>
        <p v-else :class="{ rubric: block.kind === 'rubric' }">{{ block.text }}</p>
      </template>
      <p class="source-credit"><strong>{{ text.prayers.source }}:</strong> <a :href="work.source_url" target="_blank" rel="noreferrer">{{ work.credit }}</a></p>
    </article>
    <p v-else class="status" role="status">{{ message }}</p>
  </MobileShell>
</template>
