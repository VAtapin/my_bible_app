<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { BibleChapter, BibleVerse } from '@/api/contracts'
import type { ChapterService } from '@/services/chapterService'
import { readWebChapter } from '@/services/webBibleLibrary'
import { validateContinuation } from '@/services/continuousReading'
import { bookmarkKey } from '@/offline/libraryRepository'
import { createLongPress } from '@/services/readerActions'
import { useI18n } from '@/i18n'
import { continuousReadingMessages } from '@/i18n/continuousReading'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
const props = defineProps<{ initial: BibleChapter; service: ChapterService; initialVerse?: number; initialOffset?: number; selectedVerse?: number; selectedChapter?: number; bookmarks: Set<string>; followVerse?:number; followRequest?:number }>()
const emit = defineEmits<{ visible: [chapter: BibleChapter, first: BibleVerse, last: BibleVerse, offset: number]; select: [chapter: BibleChapter, verse: BibleVerse]; bookmark: [chapter: BibleChapter, verse: BibleVerse]; actions: [chapter: BibleChapter, verse: BibleVerse, event?: MouseEvent] }>()
const { language, messages } = useI18n(), text = computed(() => continuousReadingMessages[language.value])
const segments = ref<BibleChapter[]>([props.initial]), viewport = ref<HTMLElement>(), loading = ref(false), failedDirection = ref<1 | -1>(), stopped = ref(false)
const firstNumber = computed(() => segments.value[0]!.chapter.number), lastNumber = computed(() => segments.value.at(-1)!.chapter.number)
let timer: ReturnType<typeof setTimeout> | undefined
let pressed: { chapter: BibleChapter; verse: BibleVerse } | undefined
const longPress = createLongPress(() => { if (pressed) emit('actions', pressed.chapter, pressed.verse) })
function startPress(event: PointerEvent, chapter: BibleChapter, verse: BibleVerse) { if (event.pointerType !== 'mouse') { pressed = { chapter, verse }; longPress.start(event.clientX, event.clientY) } }
async function load(direction: 1 | -1) {
  if (loading.value || stopped.value) return
  const number = direction === 1 ? lastNumber.value + 1 : firstNumber.value - 1
  if (number < 1 || number > props.initial.book.chapters_count) return
  loading.value = true; failedDirection.value = undefined
  try {
    const value = validateContinuation(props.initial, await readWebChapter(props.service, props.initial.translation.code, props.initial.book.slug, number), number)
    if (stopped.value) return
    const element = viewport.value
    const oldHeight = element?.scrollHeight ?? 0, oldTop = element?.scrollTop ?? 0
    segments.value = direction === 1 ? [...segments.value, value] : [value, ...segments.value]
    await nextTick()
    if (direction === -1 && element) element.scrollTop = oldTop + element.scrollHeight - oldHeight
  } catch { if (!stopped.value) failedDirection.value = direction }
  finally { if (!stopped.value) { loading.value = false; if (!failedDirection.value) probe() } }
}
function probe() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    const element = viewport.value; if (!element || stopped.value) return
    const area = element.getBoundingClientRect()
    const contentTop = area.top + (element.querySelector('.chapter-heading')?.getBoundingClientRect().height ?? 0)
    const rows = [...element.querySelectorAll<HTMLElement>('[data-book-verse]')].filter(row => { const rect = row.getBoundingClientRect(); return rect.bottom > contentTop && rect.top < area.bottom })
    const top = rows[0]
    const source = top && segments.value.find(c => c.chapter.number === Number(top.dataset.chapter))
    if (source && top) {
      const verse = source.verses.find(v => v.number === Number(top.dataset.bookVerse))!
      const last = rows.filter(r => Number(r.dataset.chapter) === source.chapter.number).at(-1)
      const lastVerse = source.verses.find(v => v.number === Number(last?.dataset.bookVerse)) ?? verse
      emit('visible', source, verse, lastVerse, Math.max(0, contentTop - top.getBoundingClientRect().top))
    }
    if (!loading.value && !failedDirection.value) {
      if (element.scrollHeight - element.scrollTop - element.clientHeight < 350 && lastNumber.value < props.initial.book.chapters_count) void load(1)
      else if (element.scrollTop < 180 && firstNumber.value > 1) void load(-1)
    }
  }, 180)
}
onMounted(async () => {
  await nextTick()
  const element = viewport.value, row = element?.querySelector<HTMLElement>(`[data-chapter="${props.initial.chapter.number}"][data-book-verse="${props.initialVerse}"]`)
  if (element && row) {
    const headingHeight = element.querySelector('.chapter-heading')?.getBoundingClientRect().height ?? 0
    element.scrollTop += row.getBoundingClientRect().top - element.getBoundingClientRect().top - headingHeight + Math.min(props.initialOffset ?? 0, row.getBoundingClientRect().height - 1)
  }
  probe()
})
onUnmounted(() => { stopped.value = true; clearTimeout(timer); longPress.cancel() })
watch(()=>[props.followVerse,props.followRequest],async ([number])=>{
  if(number===undefined)return
  await nextTick()
  const element=viewport.value,row=element?.querySelector<HTMLElement>(`[data-chapter="${props.initial.chapter.number}"][data-book-verse="${number}"]`)
  if(element&&row)element.scrollTop+=row.getBoundingClientRect().top-element.getBoundingClientRect().top-(element.querySelector('.chapter-heading')?.getBoundingClientRect().height??0)
})
</script>
<template>
  <div ref="viewport" class="continuous-scroll" tabindex="0" :aria-label="initial.book.name" @scroll="probe">
    <p v-if="failedDirection === -1" role="alert">{{ text.unavailable }} <button @click="load(-1)">{{ text.retry }}</button></p>
    <section v-for="chapter in segments" :key="chapter.chapter.number" :data-stream-chapter="chapter.chapter.number">
      <h3 class="chapter-heading">{{ chapter.book.name }} · {{ messages.chapter }} {{ chapter.chapter.number }}</h3>
      <p v-if="!chapter.verses.length">{{ text.empty }}</p>
      <ol>
        <li v-for="verse in chapter.verses" :key="verse.osis_ref" :data-chapter="chapter.chapter.number" :data-book-verse="verse.number" :class="{ 'selected-verse': selectedChapter === chapter.chapter.number && selectedVerse === verse.number }">
          <button type="button" class="bookmark-button" :class="{ active: bookmarks.has(bookmarkKey(chapter.translation.code, chapter.book.slug, chapter.chapter.number, verse.number)) }" :aria-label="`${messages.storage.bookmarks}: ${chapter.chapter.number}:${verse.number}`" @click="emit('bookmark', chapter, verse)">{{ bookmarks.has(bookmarkKey(chapter.translation.code, chapter.book.slug, chapter.chapter.number, verse.number)) ? '★' : '☆' }}</button>
          <button type="button" class="verse-text" :aria-pressed="selectedChapter === chapter.chapter.number && selectedVerse === verse.number" @click="emit('select', chapter, verse)" @contextmenu.prevent="emit('actions', chapter, verse, $event)" @pointerdown="startPress($event, chapter, verse)" @pointermove="longPress.move($event.clientX, $event.clientY)" @pointerup="longPress.cancel()" @pointercancel="longPress.cancel()" @pointerleave="longPress.cancel()"><span class="verse-number">{{ verse.number }}</span>{{ verse.plain_text || bibleCatalogMessages[language].catalog_verse_missing }}</button>
        </li>
      </ol>
    </section>
    <p v-if="loading" role="status">{{ messages.loading }}</p>
    <p v-if="failedDirection === 1" role="alert">{{ text.unavailable }} <button @click="load(1)">{{ text.retry }}</button></p>
  </div>
</template>
<style scoped>.continuous-scroll { height:65dvh; overflow:auto; overflow-anchor:none; overscroll-behavior:contain; position:relative }.chapter-heading { position:sticky; top:0; z-index:1; padding:8px 12px; margin:0; background:var(--white); font-size:16px } ol { margin-top:0 } </style>
