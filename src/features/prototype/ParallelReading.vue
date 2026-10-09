<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { BibleChapter, TranslationSummary } from '@/api/contracts'
import { bibleApi } from '@/api'
import type { ChapterService } from '@/services/chapterService'
import { compareVerses, loadComparison } from '@/services/bibleComparison'
import { useI18n } from '@/i18n'

const props = defineProps<{ primary: BibleChapter; catalog: TranslationSummary[]; service: ChapterService; selectedVerse?: number }>()
const emit = defineEmits<{ select: [number: number] }>()
const { messages: text } = useI18n()
const code = ref(localStorage.getItem('bible-desktop:compare-translation') ?? '')
const mode = ref(localStorage.getItem('bible-desktop:compare-mode') === 'panes' ? 'panes' : 'interleaved')
const secondary = ref<BibleChapter>()
const failed = ref(false)
const retry = ref(0)
const root = ref<HTMLElement>()
const paneHeight = ref(300)
function fitPanes() {
  const top = root.value?.querySelector('.panes')?.getBoundingClientRect().top
  if (top !== undefined) paneHeight.value = Math.max(200, window.innerHeight - top - 88)
}
let observer: ResizeObserver | undefined
onMounted(() => {
  window.addEventListener('resize', fitPanes)
  observer = new ResizeObserver(fitPanes)
  if (root.value) observer.observe(root.value)
})
onUnmounted(() => { window.removeEventListener('resize', fitPanes); observer?.disconnect() })
const rows = computed(() => secondary.value ? compareVerses(props.primary, secondary.value) : [])
watch(mode, value => localStorage.setItem('bible-desktop:compare-mode', value))
watch([() => props.primary, code, retry], async (_, __, onCleanup) => {
  let stale = false
  onCleanup(() => { stale = true })
  secondary.value = undefined
  failed.value = false
  if (!props.catalog.some(item => item.code === code.value) || code.value === props.primary.translation.code) {
    code.value = props.catalog.find(item => item.code !== props.primary.translation.code)?.code ?? ''
    return
  }
  localStorage.setItem('bible-desktop:compare-translation', code.value)
  try {
    const value = await loadComparison(props.primary, code.value, bibleApi, props.service)
    if (!stale) secondary.value = value
  } catch { if (!stale) failed.value = true }
}, { immediate: true })
watch([secondary, mode, () => props.selectedVerse], async () => {
  await nextTick()
  fitPanes()
  root.value?.querySelectorAll(`[data-primary-verse="${props.selectedVerse}"]`).forEach(element => element.scrollIntoView({ block: 'center' }))
})
function font(chapter: BibleChapter) {
  return chapter.translation.language.code === 'cu' ? 'Ponomar, serif'
    : chapter.translation.language.code === 'cu-civil' ? '"Monomakh Unicode", serif' : 'Georgia, serif'
}
</script>

<template>
  <section ref="root" class="parallel-reading">
    <div class="parallel-controls">
      <label>{{ text.parallel.translation }}<select v-model="code"><option v-for="item in catalog.filter(item => item.code !== primary.translation.code)" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
      <label>{{ text.parallel.mode }}<select v-model="mode"><option value="interleaved">{{ text.parallel.interleaved }}</option><option value="panes">{{ text.parallel.panes }}</option></select></label>
    </div>
    <p class="parallel-hint">{{ text.parallel.numbering }}</p>
    <p v-if="failed" role="alert">{{ text.parallel.error }} <button type="button" @click="retry++">{{ text.parallel.retry }}</button></p>
    <RouterLink v-else-if="!code" to="/bibles">{{ text.setup.addTranslations }}</RouterLink>
    <p v-else-if="!secondary" role="status">{{ text.loading }}</p>
    <div v-else :class="['parallel-content', mode]" :style="mode === 'panes' ? { height: `${paneHeight}px` } : undefined">
      <template v-if="mode === 'interleaved'">
        <div v-for="row in rows" :key="row.reference" class="parallel-row" :data-primary-verse="row.primary?.number" :class="{ 'selected-verse': row.primary?.number === selectedVerse }">
          <div v-for="(value, index) in [row.primary, row.secondary]" :key="index" class="parallel-verse" :style="{ fontFamily: font(index === 0 ? primary : secondary) }">
            <small>{{ (index === 0 ? primary : secondary).translation.name }}</small>
            <button v-if="index === 0 && value" type="button" class="verse-text" :aria-pressed="value.number === selectedVerse" @click="emit('select', value.number)"><span class="verse-number">{{ value.number }}</span>{{ value.plain_text }}</button>
            <p v-else><span v-if="value" class="verse-number">{{ value.number }}</span>{{ value?.plain_text || text.parallel.missing }}</p>
          </div>
        </div>
      </template>
      <template v-else>
        <section v-for="(value, index) in [primary, secondary]" :key="value.translation.code" class="parallel-pane" :aria-label="value.translation.name" tabindex="0">
          <h3>{{ value.translation.name }} · {{ value.book.name }} {{ value.chapter.number }}</h3>
          <div v-for="row in rows" :key="row.reference" :data-primary-verse="row.primary?.number" :class="{ 'selected-verse': row.primary?.number === selectedVerse }" class="parallel-verse" :style="{ fontFamily: font(value) }">
            <button v-if="index === 0 && row.primary" type="button" class="verse-text" :aria-pressed="row.primary.number === selectedVerse" @click="emit('select', row.primary.number)"><span class="verse-number">{{ row.primary.number }}</span>{{ row.primary.plain_text || text.parallel.missing }}</button>
            <p v-else><span class="verse-number">{{ (index === 0 ? row.primary : row.secondary)?.number ?? row.primary?.number ?? row.secondary?.number }}</span>{{ (index === 0 ? row.primary : row.secondary)?.plain_text || text.parallel.missing }}</p>
          </div>
        </section>
      </template>
    </div>
  </section>
</template>

<style scoped>
.parallel-controls { display: flex; flex-wrap: wrap; gap: 8px; padding: 8px 0; font-size: 14px; }
.parallel-controls label { flex: 1 1 130px; min-width: 0; }
.parallel-controls select { display: block; width: 100%; margin-top: 6px; }
.parallel-hint { font-size: 12px; padding: 0; }
.parallel-verse { padding: 10px 12px; font-size: var(--reading-size, 19px); line-height: 1.55; overflow-wrap: anywhere; }
.parallel-verse p { margin: 0; }
.parallel-verse small { display: block; font: 12px sans-serif; margin-bottom: 5px; }
.parallel-row { border-bottom: 1px solid var(--line); }
.panes { display: flex; flex-direction: column; min-height: 200px; gap: 8px; }
.parallel-pane { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; border: 1px solid var(--line); border-radius: 8px; }
.parallel-pane h3 { position: sticky; top: 0; margin: 0; padding: 8px 12px; background: var(--white, white); font-size: 14px; z-index: 1; }
</style>
