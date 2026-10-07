import { describe, expect, it } from 'vitest'
import { prayerBlocks, prayerExcerpt } from './prayerContent'
import { prayerEdition } from './prayerEditions'
import type { LiturgicalWorkSummary } from '@/api/contracts'

describe('prayer presentation', () => {
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
