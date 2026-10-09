import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getInterfaceLanguage, getMessages, initializeInterfaceLanguage, setInterfaceLanguage } from './index'
import { languageForHostname, languageSwitchUrl } from './locale'

describe('interface language', () => {
  beforeEach(() => {
    const values = new Map<string, string>()
    vi.stubGlobal('window', {
      location: { hostname: 'localhost' },
      localStorage: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
      },
    })
    vi.stubGlobal('navigator', { language: 'ru-RU' })
    vi.stubGlobal('document', {
      documentElement: { lang: 'ru' },
      title: '',
      querySelector: () => null,
    })
  })

  it('switches all shared interface messages to German', () => {
    setInterfaceLanguage('de')

    expect(getMessages().setup.manualEyebrow).toBe('MANUELLE EINRICHTUNG')
    expect(getMessages().reader.openChapter).toBe('Kapitel öffnen')
    expect(document.documentElement.lang).toBe('de')
    expect(document.title).toBe('Bible App — Meine App')
  })

  it('uses Russian by default on biblia-app.ru regardless of browser language', () => {
    expect(languageForHostname('biblia-app.ru', 'de-DE')).toBe('ru')
    expect(languageForHostname('www.biblia-app.ru', 'de-DE')).toBe('ru')
  })

  it('uses German by default on the German domains regardless of browser language', () => {
    expect(languageForHostname('biblia-app.de', 'ru-RU')).toBe('de')
    expect(languageForHostname('www.biblia-app.de', 'ru-RU')).toBe('de')
    expect(languageForHostname('bible-app.de', 'ru-RU')).toBe('de')
    expect(languageForHostname('www.bible-app.de', 'ru-RU')).toBe('de')
  })

  it('uses the browser language on bible-app.online and other hosts', () => {
    expect(languageForHostname('bible-app.online', 'de-DE')).toBe('de')
    expect(languageForHostname('bible-app.online', 'ru-RU')).toBe('ru')
    expect(languageForHostname('localhost', 'de-DE')).toBe('de')
  })

  it('links language switches to the canonical Russian and German domains', () => {
    expect(languageSwitchUrl('de', 'https://biblia-app.ru/setup/manual?edit=1#language'))
      .toBe('https://bible-app.de/setup/manual?edit=1#language')
    expect(languageSwitchUrl('ru', 'https://bible-app.online/today'))
      .toBe('https://biblia-app.ru/today')
    expect(languageSwitchUrl('de', 'https://biblia-app.de/today'))
      .toBe('https://bible-app.de/today')
    expect(languageSwitchUrl('de', 'http://localhost:5173/setup/manual')).toBeNull()
  })

  it('keeps an explicitly saved language ahead of the domain default', () => {
    setInterfaceLanguage('de')

    initializeInterfaceLanguage()

    expect(getInterfaceLanguage()).toBe('de')
  })
  it.each(['uk', 'en'] as const)('detects, switches and persists %s without inventing a new domain', (language) => {
    expect(languageForHostname('localhost', `${language}-UA`)).toBe(language)
    setInterfaceLanguage(language)
    expect(initializeInterfaceLanguage()).toBe(language)
    expect(document.documentElement.lang).toBe(language)
    expect(languageSwitchUrl(language, 'https://biblia-app.ru/')).toBeNull()
    expect(getMessages().welcome.quickTitle).not.toBe(getMessages('ru').welcome.quickTitle)
  })
  it('translates every interface key and preserves interpolation placeholders in all four languages', () => {
    function entries(value: Record<string, unknown>, prefix = ''): [string, string][] {
      return Object.entries(value).flatMap(([key, part]) => typeof part === 'string' ? [[`${prefix}${key}`, part] as [string, string]] : entries(part as Record<string, unknown>, `${prefix}${key}.`))
    }
    const original = entries(getMessages('ru'))
    for (const language of ['de', 'uk', 'en'] as const) {
      const translated = entries(getMessages(language))
      expect(translated.map(([key]) => key).sort()).toEqual(original.map(([key]) => key).sort())
      for (const [key, text] of original) expect((translated.find(([path]) => path === key)![1].match(/\{\w+\}/g) ?? []).sort(), key).toEqual((text.match(/\{\w+\}/g) ?? []).sort())
    }
  })
  it('gives a shared language URL priority over a saved language and hostname', () => {
    setInterfaceLanguage('de')
    window.location.pathname = '/uk'
    expect(initializeInterfaceLanguage()).toBe('uk')
    window.location.pathname = '/en/'
    expect(initializeInterfaceLanguage()).toBe('en')
  })
})
