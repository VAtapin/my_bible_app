/** Only a single downward pull beginning at the app's upper edge opens the tools.
 * Vertical scrolling that starts in the reading text is never intercepted. */
export function attachReaderToolbarGesture(element: HTMLElement, open: () => void) {
  let start: { id: number; x: number; y: number; time: number } | undefined
  const cancel = () => { start = undefined }
  const begin = (event: TouchEvent) => {
    cancel()
    if (event.touches.length !== 1 || window.getSelection()?.toString()) return
    const touch = event.touches[0]!, rect = element.getBoundingClientRect()
    if (touch.clientY < rect.top || touch.clientY > rect.top + 24 || touch.clientX < rect.left || touch.clientX > rect.right) return
    start = { id: touch.identifier, x: touch.clientX, y: touch.clientY, time: Date.now() }
  }
  const move = (event: TouchEvent) => {
    if (!start) return
    const touch = Array.from(event.touches).find(t => t.identifier === start!.id)
    if (event.touches.length !== 1 || !touch) { cancel(); return }
    const dx = touch.clientX - start.x, dy = touch.clientY - start.y
    if (dy < -12 || Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy)) { cancel(); return }
    if (dy > 12 && Math.abs(dy) > Math.abs(dx) * 1.5 && event.cancelable) event.preventDefault()
  }
  const end = (event: TouchEvent) => {
    const initial = start; cancel()
    if (!initial || event.touches.length || Date.now() - initial.time > 1000 || window.getSelection()?.toString()) return
    const touch = Array.from(event.changedTouches).find(t => t.identifier === initial.id)
    if (touch && touch.clientY - initial.y >= 56 && touch.clientY - initial.y > Math.abs(touch.clientX - initial.x) * 1.5) open()
  }
  element.addEventListener('touchstart', begin, { passive: true, capture: true })
  element.addEventListener('touchmove', move, { passive: false, capture: true })
  element.addEventListener('touchend', end, { passive: true, capture: true })
  element.addEventListener('touchcancel', cancel, { passive: true, capture: true })
  return () => {
    element.removeEventListener('touchstart', begin, true); element.removeEventListener('touchmove', move, true)
    element.removeEventListener('touchend', end, true); element.removeEventListener('touchcancel', cancel, true)
  }
}
