import { describe, expect, it, vi } from 'vitest'
import { ApiError, createBibleApi } from './client'

describe('Bible API client', () => {
  it('loads a chapter using the confirmed Bible Desktop route', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        data: {
          translation: {
            code: 'L1_RST',
            name: 'Синодальный',
            short_name: 'RST',
            language: { code: 'ru', name: 'Русский' },
          },
          book: { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', chapters_count: 50 },
          chapter: { number: 1, verses_count: 1 },
          verses: [{
            id: 1,
            number: 1,
            osis_ref: 'Gen.1.1',
            text: 'В начале',
            plain_text: 'В начале',
            has_strong_markup: false,
          }],
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const api = createBibleApi({ baseUrl: 'https://example.test/api/', fetcher })

    const chapter = await api.getChapter('L1_RST', 'genesis', 1)

    expect(fetcher).toHaveBeenCalledWith(
      'https://example.test/api/translations/L1_RST/books/genesis/chapters/1',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(chapter.verses[0]?.plain_text).toBe('В начале')
  })

  it('distinguishes an HTTP error', async () => {
    const api = createBibleApi({
      baseUrl: 'https://example.test/api',
      fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 404 })),
    })

    await expect(api.getChapter('missing', 'genesis', 1)).rejects.toMatchObject<ApiError>({
      kind: 'http',
      status: 404,
    })
  })

  it('rejects a malformed critical response', async () => {
    const api = createBibleApi({
      baseUrl: 'https://example.test/api',
      fetcher: vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ data: { verses: 'not-an-array' } }), { status: 200 }),
      ),
    })

    await expect(api.getChapter('L1_RST', 'genesis', 1)).rejects.toMatchObject<ApiError>({
      kind: 'invalid-response',
    })
  })
})
