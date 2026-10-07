import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLongPress, verseTarget } from './readerActions'
describe('reader actions', () => {
  afterEach(() => vi.useRealTimers())
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
