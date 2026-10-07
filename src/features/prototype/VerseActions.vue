<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { BibleChapter } from '@/api/contracts'
import { bookmarkKey } from '@/offline/libraryRepository'
import { createVerseNoteRepository } from '@/offline/verseNotes'
import { useI18n } from '@/i18n'

const props = defineProps<{ chapter?: BibleChapter; selectedVerse?: number }>()
const emit = defineEmits<{ message: [value: string] }>()
const router = useRouter()
const { messages: text } = useI18n()
const repository = createVerseNoteRepository()
const dialog = ref<HTMLDialogElement>()
const textarea = ref<HTMLTextAreaElement>()
const mode = ref<'menu' | 'note' | 'share'>('menu')
const snippet = ref('')
const draft = ref('')
const busy = ref(false)
const error = ref('')
const verse = computed(() => props.chapter?.verses.find((item) => item.number === props.selectedVerse))
const reference = computed(() => props.chapter ? `${props.chapter.book.name} ${props.chapter.chapter.number}${verse.value ? `:${verse.value.number}` : ''}` : '')
const noteKey = computed(() => props.chapter && verse.value ? bookmarkKey(props.chapter.translation.code, props.chapter.book.slug, props.chapter.chapter.number, verse.value.number) : undefined)

async function open(action: 'menu' | 'note' | 'share' = 'menu', selectedText = ''): Promise<void> {
  if (!props.chapter) return
  if (action === 'note' && !verse.value) { emit('message', text.value.readerActions.selectVerse); return }
  mode.value = action
  snippet.value = selectedText.trim()
  error.value = ''
  if (!dialog.value?.open) dialog.value?.showModal()
  if (action === 'note') {
    busy.value = true
    draft.value = ''
    try { draft.value = (await repository.get(noteKey.value!))?.text ?? '' }
    catch { error.value = text.value.readerActions.noteFailed }
    finally { busy.value = false; await nextTick(); textarea.value?.focus() }
  }
}
async function saveNote(): Promise<void> {
  if (!noteKey.value || !draft.value.trim()) return
  busy.value = true
  try {
    await repository.save({ key: noteKey.value, text: draft.value.trim(), updatedAt: new Date().toISOString() })
    dialog.value?.close()
    emit('message', text.value.readerActions.noteSaved)
  } catch { error.value = text.value.readerActions.noteFailed }
  finally { busy.value = false }
}
function readingUrl(): string {
  const chapter = props.chapter!
  const url = new URL('/reader', window.location.origin)
  url.search = new URLSearchParams({ translation: chapter.translation.code, book: chapter.book.slug, chapter: String(chapter.chapter.number), ...(verse.value ? { verse: String(verse.value.number) } : {}) }).toString()
  return url.href
}
async function share(copy = false): Promise<void> {
  if (!props.chapter) return
  try {
    const url = readingUrl()
    if (!copy && navigator.share) await navigator.share({ title: reference.value, text: snippet.value || verse.value?.plain_text, url })
    else { await navigator.clipboard.writeText(url); emit('message', text.value.readerActions.shared) }
    dialog.value?.close()
  } catch (failure) { if (!(failure instanceof DOMException && failure.name === 'AbortError')) error.value = text.value.readerActions.shareFailed }
}
async function search(): Promise<void> {
  dialog.value?.close()
  await router.push({ path: '/search', query: { translation: props.chapter?.translation.code, q: snippet.value || verse.value?.plain_text || '' } })
}
defineExpose({ open })
</script>
<template>
  <dialog ref="dialog" class="verse-dialog" :aria-label="mode === 'note' ? text.readerActions.note : text.readerActions.actions" @click="($event.target === dialog) && !busy && dialog?.close()">
    <header><strong>{{ reference }}</strong><button type="button" :disabled="busy" :aria-label="text.calendar.close" @click="dialog?.close()">×</button></header>
    <template v-if="mode === 'menu'">
      <button type="button" class="verse-menu-action" @click="mode = 'share'">{{ text.readerActions.share }}</button>
      <button type="button" class="verse-menu-action" @click="search">{{ text.readerActions.search }}</button>
      <button type="button" class="verse-menu-action" :disabled="!verse" @click="open('note', snippet)">{{ text.readerActions.addNote }}</button>
      <small v-if="!verse">{{ text.readerActions.selectVerse }}</small>
    </template>
    <template v-else-if="mode === 'share'">
      <button type="button" class="verse-menu-action" @click="share()">{{ text.readerActions.share }}</button>
      <button type="button" class="verse-menu-action" @click="share(true)">{{ text.readerActions.copyLink }}</button>
    </template>
    <form v-else @submit.prevent="saveNote">
      <label :for="'verse-note'">{{ text.readerActions.note }}</label>
      <textarea id="verse-note" ref="textarea" v-model="draft" rows="5" maxlength="10000" :disabled="busy" />
      <small>{{ text.readerActions.noteLocal }}</small>
      <button type="submit" class="primary-action" :disabled="busy || !draft.trim()">{{ text.readerActions.saveNote }}</button>
    </form>
    <p v-if="error" role="status">{{ error }}</p>
  </dialog>
</template>
