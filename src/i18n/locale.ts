export const interfaceLanguageIds = ['ru', 'de'] as const

export type InterfaceLanguage = typeof interfaceLanguageIds[number]

const canonicalLanguageHosts: Record<InterfaceLanguage, string> = {
  ru: 'biblia-app.ru',
  de: 'bible-app.de',
}

const productionHosts = ['biblia-app.ru', 'biblia-app.de', 'bible-app.de', 'bible-app.online']

export function isInterfaceLanguage(value: unknown): value is InterfaceLanguage {
  return typeof value === 'string' && interfaceLanguageIds.includes(value as InterfaceLanguage)
}

export function languageFromBrowser(language = navigator.language): InterfaceLanguage {
  return language.toLowerCase().startsWith('de') ? 'de' : 'ru'
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
  if (url.hostname === targetHostname && url.protocol === 'https:' && !url.port) return null

  url.protocol = 'https:'
  url.hostname = targetHostname
  url.port = ''
  return url.toString()
}
