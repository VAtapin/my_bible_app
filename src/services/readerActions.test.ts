import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLongPress, savedVerseLink, verseTarget } from './readerActions'
import { parseBookmarkKey } from '@/offline/libraryRepository'
describe('reader actions', () => {
  afterEach(() => vi.useRealTimers())
  it('opens existing note and bookmark keys at their exact translation, chapter and verse', () => {
    const location = parseBookmarkKey('RST-Strong:1-corinthians:13:4')!
    expect(savedVerseLink(location)).toEqual({ path: '/reader', query: { translation: 'RST-Strong', book: '1-corinthians', chapter: '13', verse: '4' } })
    for (const key of ['RST:john:0:1', 'RST:john:1:-1', 'bad', 'RST:john:1:1:2', 'RST:john:9007199254740992:1']) expect(parseBookmarkKey(key)).toBeUndefined()
  })
  it('highlights only a valid verse in the opened chapter', () => {
    expect(verseTarget('12', [1, 12, 15])).toBe(12)
    for (const value of ['0', '-1', '2', '1.2', '1<script>', ['12'], null, undefined]) expect(verseTarget(value, [1, 12, 15])).toBeUndefined()
  })
  it('opens once after holding and cancels a tap or scroll gesture', () => {
    vi.useFakeTimers()
    const open = vi.fn()
    const press = createLongPress(open)
    press.start(10, 10)
    vi.advanceTimersByTime(549)
    expect(open).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(open).toHaveBeenCalledOnce()
    press.start(10, 10)
    press.move(10, 30)
    vi.advanceTimersByTime(1000)
    expect(open).toHaveBeenCalledOnce()
    press.start(10, 10)
    press.cancel()
    vi.advanceTimersByTime(1000)
    expect(open).toHaveBeenCalledOnce()
  })
})
