import { describe, expect, it } from 'vitest'
import { normalizePrayerText, prayerBlocks, prayerExcerpt } from './prayerContent'
import { linkedPrayerEdition, prayerEdition, prayerLanguageLabel, prayerTextPresentation } from './prayerEditions'
import { ru } from '@/i18n/ru'
import { de } from '@/i18n/de'
import type { LiturgicalWorkSummary } from '@/api/contracts'

describe('prayer presentation', () => {
  const work: LiturgicalWorkSummary = { id: 4259, slug: 'prayer-5', title: 'Утренняя молитва', collections: ['prayers'], available_languages: ['cu-civil'], source_url: 'https://bible-desktop.com/api/prayers/5', editions: [{ code: 'civil', title: 'Prayer book', language: 'cu-civil', orthography: 'civil', reader_profile: 'full' }] }
  it('uses the exact linked catalogue language, not the legacy language or interface', () => {
    const edition = linkedPrayerEdition(5, [work], 'https://bible-desktop.com/api/')
    expect(prayerTextPresentation({ language_code: 'ru', text_edition: edition })).toEqual({ language: 'cu-civil', civil: true, traditional: false })
    expect(prayerTextPresentation({ language_code: 'ru' })).toEqual({ language: 'ru', civil: false, traditional: false })
    expect(prayerTextPresentation({ language_code: 'cu' }).traditional).toBe(true)
    expect(prayerTextPresentation({ language_code: 'cu', text_edition: { language: 'cu', orthography: 'civil-accented' } }).civil).toBe(true)
  })
  it('does not infer language from matching titles, unrelated sources or ambiguous editions', () => {
    expect(linkedPrayerEdition(6, [work], 'https://bible-desktop.com/api')).toBeUndefined()
    expect(linkedPrayerEdition(5, [{ ...work, source_url: 'https://foreign.test/api/prayers/5' }], 'https://bible-desktop.com/api')).toBeUndefined()
    expect(linkedPrayerEdition(5, [{ ...work, editions: [...work.editions, { ...work.editions[0]!, language: 'ru' }] }], 'https://bible-desktop.com/api')).toBeUndefined()
  })
  it('keeps paragraphs, rubrics and emphasis without any source image or link', () => {
    const blocks = prayerBlocks('<p><a href="https://foreign.test"><img src="https://foreign.test/icon.jpg"></a></p><p><em>Востав от сна</em></p><p>Во и́мя Отца́.</p><p><strong>Молитва мытаря<br><em>Лк. 18:13</em></strong></p>')
    expect(blocks).toHaveLength(3)
    expect(blocks[0]?.segments[0]).toEqual({ text: 'Востав от сна', emphasis: true, strong: false })
    expect(blocks[2]?.segments[1]).toEqual({ text: 'Лк. 18:13', emphasis: true, strong: true })
    expect(JSON.stringify(blocks)).not.toContain('foreign.test')
  })
  it('never returns executable markup, source attributes or scripts', () => {
    const blocks = prayerBlocks('<script>alert(1)</script><style>hidden</style><iframe>widget</iframe><p onclick="alert(2)">Бо&#769;же &amp; &lt;img onerror=bad&gt;</p><svg><text>foreign</text></svg>')
    expect(blocks).toEqual([{ heading: false, segments: [{ text: 'Бо́же & <img onerror=bad>', emphasis: false, strong: false }] }])
  })
  it('preserves line breaks and repairs spaces before combining accents', () => {
    expect(prayerExcerpt('<p>Го ́споди</p><p>поми́луй</p>')).toBe('Го́споди поми́луй')
    expect(prayerBlocks('Первый\n\nВторой')).toHaveLength(2)
  })
  it('repairs liturgical API text without changing words, paragraphs or traditional spelling', () => {
    expect(normalizePrayerText('Го \u0301споди, поми \u0301луй.\n\nСла \u0301ва Тебе \u0301.')).toBe('Го́споди, поми́луй.\n\nСла́ва Тебе́.')
    expect(normalizePrayerText('а\u0486\u00a0\u0301 б\t\u0483')).toBe('а\u0486\u0301 б\u0483')
    const original = 'Прїиди́те, поклони́мсѧ цр҃е́ви на́шемꙋ бг҃ꙋ.\nѰало́мъ кд҃.'
    expect(normalizePrayerText(original)).toBe(original)
  })
  it('labels the selected language rather than exposing API source branding', () => {
    expect(prayerLanguageLabel('cu', ru.setup)).toBe('Церковнославянский')
    expect(prayerLanguageLabel('cu-civil', ru.setup)).toBe('Церковнославянский · гражданский')
    expect(prayerLanguageLabel('de', de.setup)).toBe('Deutsch')
    expect(prayerLanguageLabel('cu-civil', de.setup)).toBe(de.setup.churchSlavonicCivil)
  })
  it('does not mistake civil orthography for traditional Church Slavonic', () => {
    const work: LiturgicalWorkSummary = { id: 1, slug: 'hours', title: 'Hours', collections: [], available_languages: ['cu'], source_url: null, editions: [
      { code: 'civil', title: 'Civil', language: 'cu', orthography: 'civil-accented', reader_profile: '' },
      { code: 'traditional', title: 'Traditional', language: 'cu', orthography: 'traditional', reader_profile: '' },
    ] }
    expect(prayerEdition(work, 'cu')?.code).toBe('traditional')
    expect(prayerEdition(work, 'cu-civil')?.code).toBe('civil')
    expect(prayerEdition({ ...work, editions: [work.editions[0]!] }, 'cu')).toBeUndefined()
  })
})
