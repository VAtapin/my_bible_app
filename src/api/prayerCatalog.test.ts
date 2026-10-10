import { describe, expect, it, vi } from 'vitest'
import { createBibleApi } from './client'

const groups = { short: 'Короткие молитвы', rules: 'Молитвенные правила', occasions: 'На разные случаи', initial: 'Начальные молитвы' }
const summary = {
  id: 17, canonical_slug: 'prayer-lords', liturgical_work_id: 901,
  language_code: 'cu-civil', category: 'common', liturgy_key: null,
  title: 'Отче наш', short_title: null, intro: null, excerpt: null,
  group: 'short', groups: ['short', 'occasions'], available_languages: ['cu-civil'],
  completeness: 'complete', review_status: 'source-verified', content_revision: 'a'.repeat(64), catalog_visible: true,
}
const external = { language: 'de', title: 'Actual German source', url: 'https://orthodoxia.de/gebete/gebetbuch', availability: 'external-only', offline_available: false }
const envelope = { data: [summary], catalog_version: 2, groups, external_sources: [external] }
const detail = { ...summary, body: '<p>Ѡтче нашъ.</p><p>Аминь.</p>', plain_text: 'Ѡтче нашъ.\n\nАминь.', sections: [], source_url: 'https://example.test/source' }

function setup(payload: unknown, status = 200) {
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => new Response(JSON.stringify(payload), { status }))
  return { api: createBibleApi({ baseUrl: 'https://example.test/api', fetcher }), fetcher }
}

describe('reviewed prayer catalog boundary', () => {
  it('preserves the real v2 envelope, actual script, nullable intro and distinct legacy/work IDs', async () => {
    const { api, fetcher } = setup(envelope)
    expect(await api.getPrayerCatalog!('ru')).toEqual(envelope)
    expect(await api.getPrayers('ru')).toEqual([summary])
    expect(new URL(String(fetcher.mock.calls[0]![0])).searchParams.get('language')).toBe('ru')
    const de = setup({ ...envelope, data: [] })
    expect((await de.api.getPrayerCatalog!('de')).data).toEqual([])
    expect((await de.api.getPrayerCatalog!('de')).external_sources![0]).toMatchObject({ offline_available: false, availability: 'external-only' })
  })

  it('preserves legacy catalog/detail payloads before v2 publication', async () => {
    const old = { id: 1, title: 'Old source', language_code: 'ru', category: 'common', liturgy_key: null, short_title: null, intro: null, excerpt: 'Actual excerpt' }
    expect(await setup({ data: [old] }).api.getPrayers()).toEqual([old])
    expect((await setup({ data: { ...old, body: 'Actual source body', sections: [], source_url: null } }).api.getPrayer(1)).body).toBe('Actual source body')
  })

  it('rejects incomplete v2 metadata and external copies falsely advertised as offline content', async () => {
    for (const payload of [
      { ...envelope, external_sources: undefined },
      { ...envelope, groups: [] },
      { ...envelope, data: [{ ...summary, content_revision: undefined }] },
      { ...envelope, data: [{ ...summary, available_languages: ['de'] }] },
      { ...envelope, external_sources: [{ ...external, offline_available: true }] },
      { ...envelope, external_sources: [{ ...external, url: 'javascript:alert(1)' }] },
    ]) await expect(setup(payload).api.getPrayerCatalog!()).rejects.toMatchObject({ kind: 'invalid-response' })
  })

  it('requests explicit actual editions and never relabels fallback bodies', async () => {
    const { api, fetcher } = setup({ catalog_version: 2, data: detail })
    expect((await api.getPrayer(17, 'cu-civil')).content_revision).toBe(summary.content_revision)
    expect(new URL(String(fetcher.mock.calls[0]![0])).searchParams.get('language')).toBe('cu-civil')
    await expect(api.getPrayer(17, 'de')).rejects.toMatchObject({ kind: 'invalid-response' })
    expect((await api.getPrayer(17)).language_code).toBe('cu-civil')
    await expect(api.getPrayer(18)).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(api.getPrayer(18, 'cu-civil')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(setup({ catalog_version: 2, data: { ...detail, plain_text: null } }).api.getPrayer(17)).rejects.toMatchObject({ kind: 'invalid-response' })
  })

  it.each([403, 404, 409])('propagates status %i without substituting a cached or differently translated prayer', async status => {
    await expect(setup({ message: 'Unavailable' }, status).api.getPrayer(17, 'de')).rejects.toMatchObject({ kind: 'http', status })
  })

  it('keeps verified aliases and aggregate work revision separate from a full edition hash', async () => {
    const work = { id: 901, slug: 'prayer-lords', title: 'Отче наш', collections: ['prayers'], available_languages: ['cu-civil'],
      editions: [{ code: 'ACTUAL', title: 'Source', language: 'cu-civil', orthography: 'civil-accented', reader_profile: 'prayer' }], source_url: null,
      prayer_group: 'short', prayer_groups: ['short', 'occasions'], intro: null, usage_titles: ['Перед едой'], completeness: 'complete',
      legacy_slugs: ['prayer-17', 'prayer-18'], content_revision: `${'a'.repeat(64)}:${'b'.repeat(64)}` }
    expect((await setup({ data: work }).api.getLiturgicalWork!('prayer-17')).slug).toBe('prayer-lords')
    await expect(setup({ data: work }).api.getLiturgicalWork!('prayer-unrelated')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(setup({ data: { ...work, prayer_groups: [] } }).api.getLiturgicalWork!('prayer-17')).rejects.toMatchObject({ kind: 'invalid-response' })
    const version = { slug: 'prayer-lords', title: 'Отче наш', language: 'cu-civil', edition: 'ACTUAL', edition_title: 'Source',
      orthography: 'civil-accented', reader_profile: 'prayer', blocks: [{ id: 'b1', kind: 'text', text: 'Full source beginning' }, { id: 'b2', kind: 'text', text: 'Full source ending' }],
      credit: 'Actual source', source_url: 'https://example.test/source', content_hash: 'a'.repeat(64), review_status: 'prayer-reviewed', completeness: 'complete' }
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async url => new Response(JSON.stringify({ data: String(url).includes('/versions/') ? version : work })))
    const api = createBibleApi({ baseUrl: 'https://example.test/api', fetcher })
    expect((await api.getLiturgicalVersion('prayer-17', 'cu-civil', 'ACTUAL')).blocks).toHaveLength(2)
    await expect(api.getLiturgicalVersion('prayer-17', 'de')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(api.getLiturgicalVersion('prayer-17', 'cu-civil', 'OTHER')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(api.getLiturgicalVersion('prayer-unrelated', 'cu-civil')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(setup({ data: { ...version, review_status: 'source-imported' } }).api.getLiturgicalVersion('prayer-17', 'cu-civil')).rejects.toMatchObject({ kind: 'invalid-response' })
    await expect(setup({ data: { ...version, orthography: 'traditional' } }).api.getLiturgicalVersion('prayer-17', 'cu-civil')).rejects.toMatchObject({ kind: 'invalid-response' })
  })
})
