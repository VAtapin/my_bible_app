<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, toRaw } from 'vue'
import { bibleApi } from '@/api'
import type { TranslationSummary } from '@/api/contracts'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { isInstalledPackage } from '@/offline/libraryRepository'
import { createOfflinePackageService, type PackageProgress } from '@/services/offlinePackageService'
import { localBibles, loadBibleCatalog, matchingTranslations, translationGroup, type InstalledBible } from '@/services/bibleCatalog'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { formatMessage, useI18n } from '@/i18n'

const emit = defineEmits<{ installed: [editions: TranslationSummary[]]; read: [code: string] }>()
const { language, messages: text } = useI18n()
const copy = computed(() => bibleCatalogMessages[language.value])
const chapters = createIndexedDbChapterRepository()
const library = createIndexedDbLibraryRepository()
const service = createOfflinePackageService(bibleApi, chapters, library)
const tab = ref('installed')
const local = ref<InstalledBible[]>([])
const catalog = ref<TranslationSummary[]>([])
const query = ref('')
const group = ref('')
const code = ref('')
const activeCode = ref('')
const progress = ref<PackageProgress>()
const loading = ref(false)
const error = ref('')
let controller: AbortController | undefined
let disposed = false
const editions = computed(() => tab.value === 'catalog' ? catalog.value : local.value.map(item => item.translation))
const visible = computed(() => matchingTranslations(editions.value, query.value, group.value, code.value))
const groups = computed(() => [...new Set(editions.value.map(translationGroup))].sort((a, b) => groupLabel(a).localeCompare(groupLabel(b))))
const languages = computed(() => [...new Map(editions.value.filter(item => !group.value || translationGroup(item) === group.value)
  .map(item => [item.language.code, item.language])).values()].sort((a, b) => (a.native_name ?? a.name).localeCompare(b.native_name ?? b.name)))
function groupLabel(id: string): string { return (copy.value as Record<string, string>)[`group_${id}`] ?? copy.value.group_other }
function pack(edition: TranslationSummary) { return local.value.find(item => item.translation.code === edition.code)?.package }
async function refreshLocal() {
  local.value = await localBibles(library, chapters)
  emit('installed', local.value.filter(item => isInstalledPackage(item.package)).map(item => item.translation))
}
async function showTab(id: string) {
  tab.value = id; group.value = ''; code.value = ''; query.value = ''; error.value = ''
  if (id !== 'catalog') { await refreshLocal(); return }
  loading.value = true
  try { catalog.value = await loadBibleCatalog(bibleApi) }
  catch { error.value = text.value.setup.translationsUnavailable }
  finally { loading.value = false }
}
async function install(edition: TranslationSummary) {
  if (activeCode.value) return
  activeCode.value = edition.code; error.value = ''; progress.value = undefined
  controller = new AbortController()
  try {
    // Explicit user action asks the browser to protect this existing IndexedDB from routine eviction.
    await navigator.storage?.persist?.().catch(() => false)
    await service.download(toRaw(edition), value => { progress.value = value }, controller.signal)
  } catch (reason) {
    if (!disposed) error.value = reason instanceof DOMException && reason.name === 'AbortError' ? text.value.reader.downloadStopped : copy.value.catalog_download_failed
  } finally {
    activeCode.value = ''; controller = undefined
    if (!disposed) await refreshLocal()
  }
}
onMounted(async () => { try { await refreshLocal() } catch { error.value = text.value.storage.loadFailed } })
onUnmounted(() => { disposed = true; controller?.abort() })
</script>

<template>
  <section class="bible-library" :aria-label="copy.bible_library_title">
    <div class="library-tabs">
      <button type="button" :aria-pressed="tab === 'installed'" @click="showTab('installed')">{{ copy.catalog_installed }}</button>
      <button type="button" :aria-pressed="tab === 'catalog'" @click="showTab('catalog')">{{ copy.catalog_all }}</button>
    </div>
    <label class="catalog-search">{{ copy.catalog_search }}<input v-model="query" type="search" :placeholder="copy.catalog_search" /></label>
    <div class="catalog-filters">
      <label>{{ copy.catalog_group }}<select v-model="group" @change="code = ''"><option value="">{{ copy.catalog_every }}</option><option v-for="id in groups" :key="id" :value="id">{{ groupLabel(id) }}</option></select></label>
      <label>{{ copy.catalog_language }}<select v-model="code"><option value="">{{ copy.catalog_every }}</option><option v-for="item in languages" :key="item.code" :value="item.code">{{ item.native_name ?? item.name }}</option></select></label>
    </div>
    <p>{{ formatMessage(copy.catalog_results, { count: visible.length }) }}</p>
    <p v-if="loading" role="status">{{ text.loading }}</p>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="showTab(tab)">{{ text.parallel.retry }}</button></p>
    <div v-if="!editions.length && tab === 'installed'">
      <p>{{ copy.catalog_empty }}</p><button class="primary-action" type="button" @click="showTab('catalog')">{{ copy.catalog_add }}</button>
    </div>
    <p v-else-if="!visible.length && !loading">{{ text.readerActions.noResults }}</p>
    <div class="catalog-cards">
      <article v-for="item in visible" :key="item.code" class="catalog-edition" :data-translation="item.code">
        <h3>{{ item.name }}</h3><p>{{ item.language.native_name ?? item.language.name }} · {{ item.short_name ?? item.code }}</p>
        <small>{{ item.has_old_testament && item.has_new_testament ? copy.catalog_both_testaments : item.has_new_testament ? copy.catalog_nt : copy.catalog_ot }}</small>
        <template v-if="pack(item)">
          <p>{{ pack(item)!.chapterCount }} / {{ pack(item)!.totalChapters ?? pack(item)!.chapterCount }} {{ text.reader.chapters }}</p>
          <p v-if="pack(item)!.complete || (pack(item)!.finished === undefined && isInstalledPackage(pack(item)!))">{{ text.reader.downloadReady }}</p>
          <p v-else-if="isInstalledPackage(pack(item)!)">{{ copy.catalog_installed }} · {{ text.reader.chapters }}</p>
          <p v-if="pack(item)!.unavailable?.length">{{ formatMessage(copy.catalog_missing, { count: pack(item)!.unavailable!.length }) }}</p>
          <p v-if="pack(item)!.missingVerses?.length">{{ formatMessage(copy.catalog_missing_verses, { count: pack(item)!.missingVerses!.length }) }}</p>
          <details><summary>{{ copy.catalog_details }}</summary><p>{{ (pack(item)!.approximateBytes / 1024 / 1024).toFixed(1) }} MB</p><p>{{ pack(item)!.unavailable?.join(' · ') }}</p><p>{{ pack(item)!.missingVerses?.join(' · ') }}</p></details>
          <button v-if="isInstalledPackage(pack(item)!)" type="button" @click="emit('read', item.code)">{{ copy.catalog_read }}</button>
        </template>
        <template v-if="activeCode === item.code">
          <progress :value="progress?.current ?? 0" :max="progress?.total ?? 1"></progress>
          <p>{{ progress?.bookName }} {{ progress?.chapter }} · {{ progress?.current ?? 0 }} / {{ progress?.total ?? '?' }}</p>
          <p>{{ copy.catalog_web_hint }}</p><button type="button" @click="controller?.abort()">{{ text.reader.stop }}</button>
        </template>
        <button v-else type="button" :disabled="Boolean(activeCode)" @click="install(item)">{{ pack(item)?.complete ? text.reader.update : pack(item) ? copy.catalog_resume : copy.catalog_install }}</button>
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
progress { width: 100%; }
</style>
