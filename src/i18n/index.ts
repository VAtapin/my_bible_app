import { computed, readonly, ref } from 'vue'
import { de } from './de'
import { languageForHostname, type InterfaceLanguage } from './locale'
import { ru } from './ru'

export type MessageShape<T> = {
  [Key in keyof T]: T[Key] extends string
    ? string
    : T[Key] extends Record<string, unknown>
      ? MessageShape<T[Key]>
      : T[Key]
}

const languageStorageKey = 'bible-desktop:interface-language'
const currentLanguage = ref<InterfaceLanguage>('ru')
const catalog: Record<InterfaceLanguage, MessageShape<typeof ru>> = { ru, de }

export const messages = computed(() => catalog[currentLanguage.value])

export function getInterfaceLanguage(): InterfaceLanguage {
  return currentLanguage.value
}

export function getMessages(language = currentLanguage.value): MessageShape<typeof ru> {
  return catalog[language]
}

export function initializeInterfaceLanguage(): InterfaceLanguage {
  const stored = window.localStorage.getItem(languageStorageKey)
  return setInterfaceLanguage(stored === 'de' || stored === 'ru' ? stored : languageForHostname())
}

export function setInterfaceLanguage(language: InterfaceLanguage): InterfaceLanguage {
  currentLanguage.value = language
  window.localStorage.setItem(languageStorageKey, language)
  document.documentElement.lang = language
  document.title = `${catalog[language].brand} — ${catalog[language].brandSubtitle}`
  document.querySelector('meta[name="description"]')?.setAttribute('content', catalog[language].welcome.intro)
  return language
}

export function useI18n() {
  return {
    language: readonly(currentLanguage),
    messages,
    setLanguage: setInterfaceLanguage,
  }
}

export function formatMessage(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? `{${key}}`))
}
