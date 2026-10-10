import { ref } from 'vue'
import type { KeyValueStorage } from './profileRepository'
export interface ReaderPreferences {
  chapterLabels: boolean; verseNumbers: boolean; separateVerses: boolean; headings: boolean
  crossReferences: boolean; commentaryLinks: boolean; footnotes: boolean; strongNumbers: boolean
  paragraphs: boolean; addedWords: boolean; clean: boolean; night: boolean
  fontSize: number; lineHeight: number; tapPaging: boolean; swipeChapters: boolean; swipeBooks: boolean
}
export const defaultReaderPreferences: ReaderPreferences = { chapterLabels: true, verseNumbers: true, separateVerses: true, headings: true, crossReferences: true, commentaryLinks: true, footnotes: true, strongNumbers: true, paragraphs: true, addedWords: true, clean: false, night: false, fontSize: 19, lineHeight: 1.55, tapPaging: false, swipeChapters: false, swipeBooks: false }
const key = 'bible-desktop:reader-preferences:v1'
export function normalizeReaderPreferences(value: unknown): ReaderPreferences {
  const result = { ...defaultReaderPreferences }
  if (!value || typeof value !== 'object') return result
  const input = value as Record<string, unknown>
  for (const name of Object.keys(result) as (keyof ReaderPreferences)[]) {
    if (typeof result[name] === 'boolean' && typeof input[name] === 'boolean') (result as unknown as Record<string, unknown>)[name] = input[name]
  }
  if (typeof input.fontSize === 'number' && Number.isFinite(input.fontSize)) result.fontSize = Math.max(14, Math.min(36, input.fontSize))
  if (typeof input.lineHeight === 'number' && Number.isFinite(input.lineHeight)) result.lineHeight = Math.max(1.2, Math.min(2.2, input.lineHeight))
  return result
}
export function loadReaderPreferences(storage: KeyValueStorage) { try { return normalizeReaderPreferences(JSON.parse(storage.getItem(key) || 'null')) } catch { return { ...defaultReaderPreferences } } }
const preferences = ref<ReaderPreferences>({ ...defaultReaderPreferences })
let initialized = false
export function useReaderPreferences() {
  function initialize() { if (!initialized) { preferences.value = loadReaderPreferences(window.localStorage); initialized = true } }
  function setPreferences(value: ReaderPreferences) { preferences.value = normalizeReaderPreferences(value); window.localStorage.setItem(key, JSON.stringify(preferences.value)) }
  return { preferences, initialize, setPreferences }
}
export function effectiveReaderPreferences(value: ReaderPreferences): ReaderPreferences {
  return value.clean ? { ...value, chapterLabels: false, verseNumbers: false, headings: false, crossReferences: false, commentaryLinks: false, footnotes: false, strongNumbers: false } : value
}
