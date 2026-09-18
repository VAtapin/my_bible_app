import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getMessages, setInterfaceLanguage } from './index'

describe('interface language', () => {
  beforeEach(() => {
    const values = new Map<string, string>()
    vi.stubGlobal('window', {
      localStorage: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
      },
    })
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
    expect(document.title).toBe('Bible Desktop — Meine App')
  })
})
