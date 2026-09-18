export const interfaceLanguageIds = ['ru', 'de'] as const

export type InterfaceLanguage = typeof interfaceLanguageIds[number]

export function isInterfaceLanguage(value: unknown): value is InterfaceLanguage {
  return typeof value === 'string' && interfaceLanguageIds.includes(value as InterfaceLanguage)
}

export function languageFromBrowser(language = navigator.language): InterfaceLanguage {
  return language.toLowerCase().startsWith('de') ? 'de' : 'ru'
}
