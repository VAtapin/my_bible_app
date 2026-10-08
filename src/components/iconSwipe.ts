/** Horizontal image navigation; browser keeps vertical scrolling and pinch zoom. */
export function createIconSwipe(move: (direction: number) => void) {
  let start: { id: number; x: number; y: number } | undefined
  const cancel = () => { start = undefined }
  return {
    cancel,
    start(event: PointerEvent) {
      cancel()
      if (!event.isPrimary || event.button !== 0) return
      start = { id: event.pointerId, x: event.clientX, y: event.clientY }
      // Keep the release even if the finger/pen leaves the image.
      const target = event.currentTarget as HTMLElement | null
      target?.setPointerCapture?.(event.pointerId)
    },
    end(event: PointerEvent) {
      if (!start || start.id !== event.pointerId) return
      const dx = event.clientX - start.x, dy = event.clientY - start.y
      cancel()
      if (Math.abs(dx) >= 40 && Math.abs(dx) > Math.abs(dy) * 1.5) move(dx < 0 ? 1 : -1)
    },
  }
}
