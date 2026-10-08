import { IDBFactory } from 'fake-indexeddb'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCalendarMediaCache, previewSize, previewMaxBytes, readCalendarState, writeCalendarState } from './calendarMedia'
beforeEach(() => vi.stubGlobal('indexedDB', new IDBFactory()))
describe('economical calendar previews', () => {
  it('bounds portrait and landscape dimensions without upscaling', () => {
    expect(previewSize(1000, 2000)).toEqual({ width: 160, height: 320 })
    expect(previewSize(2000, 1000)).toEqual({ width: 320, height: 160 })
    expect(previewSize(80, 90)).toEqual({ width: 80, height: 90 })
  })
  it('persists only the resized preview, deduplicates by source URL, and reads it after reopening storage', async () => {
    const original = new Blob(['full image'], { type: 'image/jpeg' })
    const preview = new Blob(['preview'], { type: 'image/webp' })
    const fetcher = vi.fn(async () => new Response(original))
    const resize = vi.fn(async () => preview)
    const cache = createCalendarMediaCache(fetcher, resize)
    await cache.save('https://example.test/icon', true)
    await cache.save('https://example.test/icon', true)
    expect(fetcher).toHaveBeenCalledOnce()
    expect(await (await createCalendarMediaCache().read('https://example.test/icon'))?.text()).toBe('preview')
    expect(resize).toHaveBeenCalledOnce()
  })
  it('rejects oversize previews instead of storing originals and leaves other state alone', async () => {
    await writeCalendarState('note:test', 'personal note')
    const cache = createCalendarMediaCache(vi.fn(async () => new Response(new Blob(['image'], { type: 'image/jpeg' }))), async () => new Blob([new Uint8Array(previewMaxBytes + 1)]))
    await expect(cache.save('https://example.test/large', true)).rejects.toThrow('Asset too large')
    expect(await cache.read('https://example.test/large')).toBeUndefined()
    expect(await readCalendarState('note:test')).toBe('personal note')
  })
  it('does not write or fetch after cancellation', async () => {
    const fetcher = vi.fn(), controller = new AbortController()
    controller.abort()
    await expect(createCalendarMediaCache(fetcher).save('https://example.test/1', true, controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('requests a server preview through the existing image endpoint, not a gallery original', async () => {
    const fetcher = vi.fn(async () => new Response(new Blob(['thumb'], { type: 'image/webp' })))
    await createCalendarMediaCache(fetcher, async (blob) => blob).save('https://bible-desktop.com/api/calendar/icons/588/images/20', true)
    expect(fetcher.mock.calls[0]?.[0]).toBe('https://bible-desktop.com/api/calendar/icons/588/images/20?preview=1')
  })
})
