export const interfaceLanguageIds = ['ru', 'de', 'uk', 'en'] as const

export type InterfaceLanguage = typeof interfaceLanguageIds[number]

const canonicalLanguageHosts: Partial<Record<InterfaceLanguage, string>> = {
  ru: 'biblia-app.ru',
  de: 'bible-app.de',
}

const productionHosts = ['biblia-app.ru', 'biblia-app.de', 'bible-app.de', 'bible-app.online']

export function isInterfaceLanguage(value: unknown): value is InterfaceLanguage {
  return typeof value === 'string' && interfaceLanguageIds.includes(value as InterfaceLanguage)
}

export function languageFromBrowser(language = navigator.language): InterfaceLanguage {
  const code = language.toLowerCase().split('-')[0]
  return isInterfaceLanguage(code) ? code : 'ru'
}

export const interfaceLanguageNames: Record<InterfaceLanguage, string> = { ru: 'Русский', de: 'Deutsch', uk: 'Українська', en: 'English' }
export const interfaceLocales: Record<InterfaceLanguage, string> = { ru: 'ru-RU', de: 'de-DE', uk: 'uk-UA', en: 'en-GB' }
// Verified public translation codes; interface language never relabels source texts.
export const defaultBibleTranslations: Record<InterfaceLanguage, string> = { ru: 'BQ_RUSSIAN_RST_STRONG', de: 'BQ_GERMAN_ELBERFELD_STRONG', uk: 'BQ_UKRAINE', en: 'BQ_ENGLISH_KJV_1769' }
export function languageFromPath(pathname = window.location.pathname): InterfaceLanguage | undefined {
  const code = /^\/(ru|de|uk|en)\/?$/.exec(pathname ?? '')?.[1]
  return isInterfaceLanguage(code) ? code : undefined
}

export function languageForHostname(
  hostname = window.location.hostname,
  browserLanguage = navigator.language,
): InterfaceLanguage {
  const normalizedHostname = hostname.toLowerCase().replace(/\.$/, '')

  if (normalizedHostname === 'biblia-app.ru' || normalizedHostname.endsWith('.biblia-app.ru')) {
    return 'ru'
  }

  if (
    normalizedHostname === 'biblia-app.de'
    || normalizedHostname.endsWith('.biblia-app.de')
    || normalizedHostname === 'bible-app.de'
    || normalizedHostname.endsWith('.bible-app.de')
  ) {
    return 'de'
  }

  return languageFromBrowser(browserLanguage)
}

export function languageSwitchUrl(
  language: InterfaceLanguage,
  currentUrl = window.location.href,
): string | null {
  const url = new URL(currentUrl)
  const normalizedHostname = url.hostname.toLowerCase().replace(/\.$/, '').replace(/^www\./, '')

  if (!productionHosts.includes(normalizedHostname)) return null

  const targetHostname = canonicalLanguageHosts[language]
  if (!targetHostname) return null
  if (url.hostname === targetHostname && url.protocol === 'https:' && !url.port) return null

  url.protocol = 'https:'
  url.hostname = targetHostname
  url.port = ''
  return url.toString()
}
