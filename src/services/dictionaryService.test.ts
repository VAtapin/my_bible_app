import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { createDictionaryService } from './dictionaryService'
import type { DictionaryApi } from '@/api/dictionaries'
vi.mock('./installedDictionaries',()=>({installedDictionaryModules:async()=>[],installedDictionaryEntries:async()=>undefined,installedDictionaryArticle:async()=>undefined,installedDictionaryContext:async()=>undefined,installedDictionaryLookup:async()=>({data:[],allInstalled:false})}))
describe('saved dictionaries', () => {
  const cache = () => { const records = new Map<string, unknown>(); return { read: async <T>(key: string) => records.get(key) as T | undefined, write: async (key: string, value: unknown) => { records.set(key, value) } } }
  it('restores an opened article only for its exact source version after network failure', async () => {
    const article = vi.fn().mockResolvedValue({ key: 'a'.repeat(40), topic: 'Title' })
    const service = createDictionaryService({ article } as unknown as DictionaryApi, cache())
    await service.article('BMaps', 'a'.repeat(40), 'v1')
    article.mockRejectedValue(new ApiError('offline', 'Network'))
    expect((await service.article('BMaps', 'a'.repeat(40), 'v1')).topic).toBe('Title')
    await expect(service.article('BMaps', 'a'.repeat(40), 'v2')).rejects.toThrow('Network')
  })
  it('never masks denied/unpublished sources with saved articles', async () => {
    const article = vi.fn().mockResolvedValue({ topic: 'Old title' }); const service = createDictionaryService({ article } as unknown as DictionaryApi, cache())
    await service.article('BMaps', 'a'.repeat(40))
    for (const status of [403, 404, 429, 500]) { article.mockRejectedValue(new ApiError('http', 'Unavailable', status)); await expect(service.article('BMaps', 'a'.repeat(40))).rejects.toMatchObject({ status }) }
  })
  it('storage quota does not prevent reading a live article', async () => {
    const service = createDictionaryService({ article: async () => ({ topic: 'Live' }) } as unknown as DictionaryApi, { read: async () => undefined, write: async () => { throw new Error('Quota') } })
    expect((await service.article('BMaps', 'a'.repeat(40))).topic).toBe('Live')
  })
})
