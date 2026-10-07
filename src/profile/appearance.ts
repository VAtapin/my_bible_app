import { ref } from 'vue'
import type { KeyValueStorage } from './profileRepository'

export type AppearanceTheme = 'classic' | 'modern' | 'warm'
const key = 'bible-desktop:appearance'
const theme = ref<AppearanceTheme>('classic')
let initialized = false

export function loadAppearance(storage: KeyValueStorage): AppearanceTheme {
  const value = storage.getItem(key)
  return value === 'warm' || value === 'modern' ? value : 'classic'
}

export function useAppearance() {
  function initialize(): void {
    if (initialized) return
    theme.value = loadAppearance(window.localStorage)
    initialized = true
  }
  function setTheme(value: AppearanceTheme): void {
    window.localStorage.setItem(key, value)
    theme.value = value
  }
  return { theme, initialize, setTheme }
}
