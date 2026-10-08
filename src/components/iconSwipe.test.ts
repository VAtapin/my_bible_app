import { describe, expect, it, vi } from 'vitest'
import { createIconSwipe } from './iconSwipe'

const pointer = (x: number, y = 100, overrides: Partial<PointerEvent> = {}) => ({
  clientX: x, clientY: y, pointerId: 1, isPrimary: true, button: 0, pointerType: 'touch', ...overrides,
}) as PointerEvent

describe('icon gallery swipe', () => {
  it('switches left to next and right to previous once per touch gesture', () => {
    const move = vi.fn(), swipe = createIconSwipe(move)
    swipe.start(pointer(200)); swipe.end(pointer(100)); swipe.end(pointer(50))
    swipe.start(pointer(100)); swipe.end(pointer(200))
    expect(move.mock.calls).toEqual([[1], [-1]])
  })
  it('captures the pointer so a release outside the image is received', () => {
    const setPointerCapture = vi.fn()
    createIconSwipe(vi.fn()).start(pointer(100, 100, { currentTarget: { setPointerCapture } as unknown as HTMLElement }))
    expect(setPointerCapture).toHaveBeenCalledWith(1)
  })
  it.each([[200, 200], [150, 140], [170, 100], [200, 100]])('ignores vertical, diagonal, short gestures and taps (%i, %i)', (x, y) => {
    const move = vi.fn(), swipe = createIconSwipe(move)
    swipe.start(pointer(200)); swipe.end(pointer(x, y))
    expect(move).not.toHaveBeenCalled()
  })
  it('ignores cancelled gestures and resets for the next swipe', () => {
    const move = vi.fn(), swipe = createIconSwipe(move)
    swipe.start(pointer(200)); swipe.cancel(); swipe.end(pointer(100))
    expect(move).not.toHaveBeenCalled()
    swipe.start(pointer(200)); swipe.end(pointer(100))
    expect(move).toHaveBeenCalledExactlyOnceWith(1)
  })
  it('does not navigate during multi-touch pinch or with another pointer release', () => {
    const move = vi.fn(), swipe = createIconSwipe(move)
    swipe.start(pointer(200)); swipe.end(pointer(100, 100, { pointerId: 2 }))
    expect(move).not.toHaveBeenCalled()
    swipe.start(pointer(100, 100, { pointerId: 2, isPrimary: false })); swipe.end(pointer(50))
    expect(move).not.toHaveBeenCalled()
  })
  it('supports pen/mouse dragging, but not secondary mouse buttons', () => {
    const move = vi.fn(), swipe = createIconSwipe(move)
    swipe.start(pointer(200, 100, { pointerType: 'mouse', button: 2 })); swipe.end(pointer(100))
    expect(move).not.toHaveBeenCalled()
    swipe.start(pointer(200, 100, { pointerType: 'pen' })); swipe.end(pointer(100))
    expect(move).toHaveBeenCalledExactlyOnceWith(1)
  })
})
