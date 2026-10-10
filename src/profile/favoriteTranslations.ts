import { ref } from 'vue'
import type { KeyValueStorage } from './profileRepository'
const key = 'bible-desktop:favorite-translations:v1'
export function loadFavoriteTranslations(storage: KeyValueStorage): string[] {
  try { const value: unknown = JSON.parse(storage.getItem(key) || '[]'); return Array.isArray(value) ? [...new Set(value.filter((v): v is string => typeof v === 'string' && !!v && v.length <= 200))].slice(0, 1000) : [] } catch { return [] }
}
const favorites = ref<string[]>([])
let initialized = false
export function useFavoriteTranslations() {
  function initialize() { if (!initialized) { favorites.value = loadFavoriteTranslations(window.localStorage); initialized = true } }
  function toggle(code: string) { favorites.value = favorites.value.includes(code) ? favorites.value.filter(c => c !== code) : [...favorites.value, code]; window.localStorage.setItem(key, JSON.stringify(favorites.value)) }
  return { favorites, initialize, toggle }
}
