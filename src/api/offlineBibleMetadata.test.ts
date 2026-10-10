import { describe, expect, it, vi } from 'vitest'
import { createBibleApi } from './client'

const edition = { code: 'TEST', name: 'Edition', short_name: null, language: { code: 'en', name: 'English' }, canon_code: null,
  has_old_testament: true, has_new_testament: true, has_apocrypha: false, has_strong: false, is_default: false }
function api(metadata: Record<string, unknown>) {
  return createBibleApi({ baseUrl: 'https://example.test/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(
    new Response(JSON.stringify({ data: [{ ...edition, ...metadata }] }))) })
}
describe('offline Bible catalog metadata', () => {
  it.each([{}, { offline_size_estimate_bytes: null, content_revision: null },
    { offline_size_estimate_bytes: 12345, content_revision: 'actual-published-revision' }])('accepts legacy and published estimates: %j', async metadata => {
    expect((await api(metadata).getTranslations())[0]).toEqual({ ...edition, ...metadata })
  })
  it.each([{ offline_size_estimate_bytes: -1 }, { offline_size_estimate_bytes: 0 }, { offline_size_estimate_bytes: 1.5 },
    { offline_size_estimate_bytes: '12345' }, { content_revision: ' ' }, { content_revision: {} }])('rejects misleading sizes/revisions: %j', async metadata => {
    await expect(api(metadata).getTranslations()).rejects.toMatchObject({ kind: 'invalid-response' })
  })
})
