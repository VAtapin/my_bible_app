<script setup lang="ts">
import { computed, onMounted, ref, toRaw } from 'vue'
import { bibleApi } from '@/api'
import type { TranslationSummary } from '@/api/contracts'
import { loadBibleCatalog, matchingTranslations, translationGroup } from '@/services/bibleCatalog'
import { enabledWebBibles, saveEnabledWebBibles } from '@/services/webBibleLibrary'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { formatMessage, useI18n } from '@/i18n'
const emit = defineEmits<{ installed: [editions: TranslationSummary[]]; read: [code: string] }>()
const props = defineProps<{ initialTab?: string }>()
const { language, messages: text } = useI18n()
const copy = computed(() => bibleCatalogMessages[language.value])
const tab = ref(props.initialTab === 'catalog' ? 'catalog' : 'enabled')
const enabled = ref<TranslationSummary[]>([]), catalog = ref<TranslationSummary[]>([])
const query = ref(''), group = ref(''), code = ref('')
const loading = ref(false), changing = ref(false), error = ref('')
const editions = computed(() => tab.value === 'catalog' ? catalog.value : enabled.value)
const visible = computed(() => matchingTranslations(editions.value, query.value, group.value, code.value))
const groups = computed(() => [...new Set(editions.value.map(translationGroup))].sort((a, b) => groupLabel(a).localeCompare(groupLabel(b))))
const languages = computed(() => [...new Map(editions.value.filter(item => !group.value || translationGroup(item) === group.value)
  .map(item => [item.language.code, item.language])).values()].sort((a, b) => (a.native_name ?? a.name).localeCompare(b.native_name ?? b.name)))
function groupLabel(id: string): string { return (copy.value as Record<string, string>)[`group_${id}`] ?? copy.value.group_other }
function isEnabled(item: TranslationSummary) { return enabled.value.some(value => value.code === item.code) }
async function load() {
  loading.value = true; error.value = ''
  try {
    enabled.value = await enabledWebBibles(bibleApi)
    emit('installed', enabled.value)
    if (tab.value === 'catalog') catalog.value = await loadBibleCatalog(bibleApi)
  } catch { error.value = text.value.setup.translationsUnavailable }
  finally { loading.value = false }
}
async function showTab(id: string) {
  tab.value = id; group.value = ''; code.value = ''; query.value = ''
  await load()
}
async function toggle(item: TranslationSummary) {
  changing.value = true; error.value = ''
  try {
    const next = isEnabled(item) ? enabled.value.filter(value => value.code !== item.code) : [...enabled.value, item]
    await saveEnabledWebBibles(next.map(value => toRaw(value)))
    enabled.value = next; emit('installed', next)
  } catch { error.value = text.value.unknownError }
  finally { changing.value = false }
}
onMounted(load)
</script>
<template>
  <section class="bible-library" :aria-label="copy.bible_library_title">
    <div class="library-tabs">
      <button type="button" :aria-pressed="tab === 'enabled'" @click="showTab('enabled')">{{ copy.catalog_installed }}</button>
      <button type="button" :aria-pressed="tab === 'catalog'" @click="showTab('catalog')">{{ copy.catalog_all }}</button>
    </div>
    <label class="catalog-search">{{ copy.catalog_search }}<input v-model="query" type="search" :placeholder="copy.catalog_search" /></label>
    <div class="catalog-filters">
      <label>{{ copy.catalog_group }}<select v-model="group" @change="code = ''"><option value="">{{ copy.catalog_every }}</option><option v-for="id in groups" :key="id" :value="id">{{ groupLabel(id) }}</option></select></label>
      <label>{{ copy.catalog_language }}<select v-model="code"><option value="">{{ copy.catalog_every }}</option><option v-for="item in languages" :key="item.code" :value="item.code">{{ item.native_name ?? item.name }}</option></select></label>
    </div>
    <p>{{ formatMessage(copy.catalog_results, { count: visible.length }) }}</p>
    <p v-if="loading" role="status">{{ text.loading }}</p>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="load">{{ text.parallel.retry }}</button></p>
    <div v-if="!editions.length && tab === 'enabled' && !loading">
      <p>{{ copy.catalog_empty }}</p><button class="primary-action" type="button" @click="showTab('catalog')">{{ copy.catalog_add }}</button>
    </div>
    <p v-else-if="!visible.length && !loading">{{ text.readerActions.noResults }}</p>
    <div class="catalog-cards">
      <article v-for="item in visible" :key="item.code" class="catalog-edition" :data-translation="item.code">
        <h3>{{ item.name }}</h3><p>{{ item.language.native_name ?? item.language.name }} · {{ item.short_name ?? item.code }}</p>
        <p><small>{{ item.has_old_testament && item.has_new_testament ? copy.catalog_both_testaments : item.has_new_testament ? copy.catalog_nt : copy.catalog_ot }}</small></p>
        <button v-if="isEnabled(item)" type="button" @click="emit('read', item.code)">{{ copy.catalog_read }}</button>
        <button type="button" :aria-pressed="isEnabled(item)" :disabled="changing || (isEnabled(item) && enabled.length === 1)" @click="toggle(item)">{{ isEnabled(item) ? copy.catalog_disable : copy.catalog_install }}</button>
      </article>
    </div>
  </section>
</template>
<style scoped>
.bible-library { display: grid; gap: 12px; min-width: 0; }
.library-tabs, .catalog-filters { display: flex; gap: 10px; }
.library-tabs button { flex: 1; }
.bible-library button, select, input { font: inherit; border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; background: var(--white, white); color: var(--ink); max-width: 100%; min-width: 0; }
.library-tabs button[aria-pressed="true"] { background: var(--blue, #315b78); color: white; }
.catalog-search, .catalog-filters label { display: grid; gap: 6px; min-width: 0; flex: 1; }
.catalog-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 12px; align-items: start; }
.catalog-edition { background: var(--white, white); padding: 16px; border: 1px solid var(--line); border-radius: 16px; overflow-wrap: anywhere; }
.catalog-edition h3 { margin: 0; font-size: 16px; }
.catalog-edition p { margin: 8px 0; }
.catalog-edition button + button { margin-inline-start: 8px; }
</style>
