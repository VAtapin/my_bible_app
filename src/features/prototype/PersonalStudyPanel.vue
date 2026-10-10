<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { BibleChapter } from '@/api/contracts'
import type { ChapterService } from '@/services/chapterService'
import { readWebChapter } from '@/services/webBibleLibrary'
import { trimWordSelection, collectPassage, passagePoint, passageReference, formatPassage, createWordMark, eraseWordMarks, markMatches, emptyPersonalStudy, type SavedPassage } from '@/services/personalStudy'
import { createPersonalStudyRepository } from '@/offline/personalStudy'
import { useI18n } from '@/i18n'
import { personalColorName, personalStudyMessages } from '@/i18n/personalStudy'
import WordMarkText from './WordMarkText.vue'
import {dictionaryLookupLink} from '@/services/dictionaryLookup'
import {dictionaryMessages} from '@/i18n/dictionaries'
import {geographyMessages} from '@/i18n/geography'
const props = defineProps<{ chapter: BibleChapter; verse: number; service: ChapterService; selectedText?: string }>()
const emit = defineEmits<{ selection: [passage: SavedPassage]; message: [value: string] }>()
const { language } = useI18n(), text = computed(() => personalStudyMessages[language.value]), repository = createPersonalStudyRepository()
const start = ref(''), end = ref(''), passage = ref<SavedPassage>(), data = ref(emptyPersonalStudy()), busy = ref(false), error = ref('')
const title = ref(''), description = ref(''), collection = ref(''), color = ref('yellow'), note = ref('')
const options = ref({ reference: true, translation: true, numbers: true }), selected = ref<{ start: number; end: number }>()
const verseData = computed(() => props.chapter.verses.find(v => v.number === props.verse)), wordQuote = computed(() => selected.value && verseData.value?.plain_text.slice(selected.value.start, selected.value.end))
const marker = ref(false)
const dictionaryLink=computed(()=>dictionaryLookupLink(wordQuote.value))
const dictionaryText=computed(()=>dictionaryMessages[language.value])
function selectedRange(start:number,end:number){selected.value=verseData.value?trimWordSelection(verseData.value.plain_text,start,end):undefined;const range=selected.value;const existing=range&&data.value.marks.find(m=>verseData.value&&markMatches(m,props.chapter.translation.code,verseData.value)&&m.start===range.start&&m.end===range.end);note.value=existing?.note??''}
const stale = computed(() => data.value.marks.some(m => m.code === props.chapter.translation.code && m.osis === verseData.value?.osis_ref && !markMatches(m, m.code, verseData.value!)))
watch(() => [props.chapter.translation.code, props.chapter.book.slug, props.chapter.chapter.number, props.verse], () => { start.value = end.value = `${props.chapter.chapter.number}:${props.verse}`; passage.value = undefined; selected.value = undefined; note.value = ''; if(props.selectedText && verseData.value){const at=verseData.value.plain_text.indexOf(props.selectedText);if(at>=0&&verseData.value.plain_text.indexOf(props.selectedText,at+1)<0)selectedRange(at,at+props.selectedText.length)} }, { immediate: true })
watch([start,end],()=>{passage.value=undefined})
onMounted(async () => { try { data.value = await repository.read() } catch { error.value = text.value.failed } })
async function select() {
  busy.value = true; error.value = ''
  try { passage.value = await collectPassage(props.chapter, passagePoint(start.value), passagePoint(end.value), number => readWebChapter(props.service, props.chapter.translation.code, props.chapter.book.slug, number)); emit('selection', passage.value) }
  catch { error.value = text.value.invalid }
  finally { busy.value = false }
}
async function save(kind: 'bookmark' | 'memory') {
  if (!passage.value) await select()
  if (!passage.value) return
  const value = JSON.parse(JSON.stringify(passage.value)) as SavedPassage
  busy.value = true
  try { data.value = await repository.update(data => kind === 'bookmark' ? { ...data, bookmarks: [...data.bookmarks, { id: crypto.randomUUID(), passage: value, title: title.value.trim() || passageReference(value), description: description.value.trim(), collection: collection.value.trim(), color: color.value, order: data.bookmarks.filter(b => b.collection === collection.value.trim()).length }] } : { ...data, cards: [...data.cards, { id: crypto.randomUUID(), passage: value, learned: false }] }); emit('message', text.value.saved) }
  catch { error.value = text.value.failed }
  finally { busy.value = false }
}
async function share(copy: boolean) {
  if (!passage.value) await select()
  if (!passage.value) return
  try { const value = formatPassage(passage.value, options.value); if (!copy && navigator.share) await navigator.share({ title: passageReference(passage.value), text: value }); else await navigator.clipboard.writeText(value); emit('message', text.value.saved) }
  catch (failure) { if (!(failure instanceof DOMException && failure.name === 'AbortError')) error.value = text.value.failed }
}
async function mark(kind: 'highlight' | 'underline' | 'note' | 'erase') {
  if (!selected.value || !verseData.value) return
  try { const { start, end } = selected.value, verse = verseData.value, code = props.chapter.translation.code
    data.value = await repository.update(data => { const marks = eraseWordMarks(data.marks, code, verse, start, end); if (kind !== 'erase') marks.push(createWordMark(code, verse, start, end, color.value, kind === 'underline', note.value.trim())); return { ...data, marks } }); emit('message', text.value.saved)
  } catch { error.value = text.value.failed }
}
</script>
<template>
  <section class="personal-panel">
    <h3>{{ text.title }}</h3>
    <form @submit.prevent="select"><label>{{ text.start }}<input v-model="start" inputmode="text" pattern="[0-9]+:[0-9]+" required :disabled="busy" /></label><label>{{ text.end }}<input v-model="end" inputmode="text" pattern="[0-9]+:[0-9]+" required :disabled="busy" /></label><button :disabled="busy">{{ text.select }}</button></form>
    <p v-if="passage"><strong>{{ passageReference(passage) }}</strong> · {{ passage.verses.length }}</p>
    <div class="format"><label><input v-model="options.reference" type="checkbox" />{{ text.reference }}</label><label><input v-model="options.translation" type="checkbox" />{{ text.translation }}</label><label><input v-model="options.numbers" type="checkbox" />{{ text.numbers }}</label></div>
    <div class="row"><button type="button" :disabled="busy" @click="share(true)">{{ text.copy }}</button><button type="button" :disabled="busy" @click="share(false)">{{ text.share }}</button></div>
    <label>{{ text.heading }}<input v-model="title" maxlength="200" /></label><label>{{ text.description }}<textarea v-model="description" rows="2" maxlength="10000" /></label><label>{{ text.collection }}<input v-model="collection" list="study-collections" maxlength="100" /></label><datalist id="study-collections"><option v-for="name in [...new Set(data.bookmarks.map(b=>b.collection).filter(Boolean))]" :key="name" :value="name" /></datalist>
    <label>{{ text.color }}<select v-model="color"><option v-for="(_,name) in data.palette" :key="name" :value="name" :style="{background:data.palette[name]?.day}">{{ personalColorName(name,language) }}</option></select></label>
    <div class="row"><button type="button" :disabled="busy" @click="save('bookmark')">{{ text.bookmark }}</button><button type="button" :disabled="busy" @click="save('memory')">{{ text.memory }}</button></div>
    <h3>{{ text.word }}</h3><p>{{ text.wordHint }}</p><button type="button" :aria-pressed="marker" @click="marker=!marker">{{ text.highlight }} ↔</button><WordMarkText v-if="verseData" :text="verseData.plain_text" :enabled="marker" @selection="selectedRange" /><p v-if="wordQuote">{{ text.selected }}: <strong>{{ wordQuote }}</strong></p>
    <RouterLink v-if="dictionaryLink" :to="dictionaryLink">{{dictionaryText.title}} · {{wordQuote}}</RouterLink>
    <RouterLink v-if="wordQuote" :to="{path:'/atlas',query:{q:wordQuote,verses:verseData?.osis_ref}}">{{geographyMessages[language].title}} · {{wordQuote}}</RouterLink>
    <label>{{ text.note }}<textarea v-model="note" rows="2" maxlength="10000" /></label><div class="row"><button :disabled="!wordQuote" @click="mark('highlight')">{{ text.highlight }}</button><button :disabled="!wordQuote" @click="mark('underline')">{{ text.underline }}</button><button :disabled="!wordQuote" @click="mark('note')">{{ text.save }}</button><button :disabled="!wordQuote" @click="mark('erase')">{{ text.erase }}</button></div>
    <p v-if="stale" role="status">{{ text.stale }}</p><p v-if="error" role="alert">{{ error }}</p><RouterLink to="/storage?tab=study">{{ text.library }} →</RouterLink>
  </section>
</template>
<style scoped>.personal-panel{display:grid;gap:10px}.personal-panel label{display:grid;gap:4px}.personal-panel input:not([type=checkbox]),.personal-panel textarea,.personal-panel select{width:100%;box-sizing:border-box;min-height:40px}.personal-panel form,.row{display:flex;gap:8px;flex-wrap:wrap}.format{display:flex;gap:12px;flex-wrap:wrap}.format label{display:flex;align-items:center}.selectable-words{user-select:text;touch-action:pan-y;white-space:pre-wrap;line-height:1.6;padding:12px;background:var(--white)}</style>
