import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BibleLibrary from './BibleLibrary.vue'
import { getInterfaceLanguage, setInterfaceLanguage } from '@/i18n'
import { bibleCatalogMessages } from '@/i18n/bibleCatalog'
import { interfaceLanguageIds } from '@/i18n/locale'

const originalLanguage = getInterfaceLanguage()
beforeEach(() => {
  vi.stubGlobal('window', { localStorage: { setItem: vi.fn() } })
  vi.stubGlobal('document', { documentElement: { lang: '' }, title: '', querySelector: () => null })
})
afterEach(() => { setInterfaceLanguage(originalLanguage); vi.unstubAllGlobals() })
describe('direct catalogue entry from enabled translations', () => {
  it.each(interfaceLanguageIds)('shows one prominent direct action before search in %s', async language => {
    setInterfaceLanguage(language)
    const html = await renderToString(createSSRApp(BibleLibrary))
    expect(html).toContain(bibleCatalogMessages[language].catalog_add_other)
    expect(html.match(/data-testid="catalog-add-other"/g)).toHaveLength(1)
    expect(html.indexOf('data-testid="catalog-add-other"')).toBeLessThan(html.indexOf('class="catalog-search"'))
    expect(html).not.toContain('class="primary-action"') // No duplicate action in the empty-state panel.
  })
  it('does not offer opening the catalogue while already viewing it', async () => {
    const html = await renderToString(createSSRApp(BibleLibrary, { initialTab: 'catalog' }))
    expect(html).not.toContain('data-testid="catalog-add-other"')
  })
})
