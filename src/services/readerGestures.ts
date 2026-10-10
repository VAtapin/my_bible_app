import{measuredReaderLines,measuredPageDistance,firstReadingLineDistance}from'./readerPaging'
export interface ReaderGestureOptions { tapPaging: boolean; swipeChapters: boolean; swipeBooks: boolean }
export interface ReaderGestureActions { page(direction: number): void; chapter(direction: number): void; book(direction: number): void }
const blocked = (target: EventTarget | null) => target instanceof Element && (!!target.closest('a,input,select,textarea,summary,[role="button"],[role="separator"],[data-no-reader-gesture]') || !!target.closest('button:not(.verse-text)'))
/** Does not prevent ordinary vertical scroll, link activation, selection or divider resizing. */
export function attachReaderGestures(element: HTMLElement, options: () => ReaderGestureOptions, actions: ReaderGestureActions) {
  const pointers = new Map<number, { x: number; y: number }>()
  let startX = 0, startY = 0, fingers = 0, moved = false, rejected = false, started = 0
  function down(e: PointerEvent) {
    if (!pointers.size) { started=Date.now();startX = e.clientX; startY = e.clientY; fingers = 0; moved = false; rejected = blocked(e.target) || !!window.getSelection()?.toString() }
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY }); fingers = Math.max(fingers, pointers.size)
  }
  function move(e: PointerEvent) { if (pointers.has(e.pointerId) && Math.hypot(e.clientX - startX, e.clientY - startY) > 12) moved = true }
  function up(e: PointerEvent) {
    if (!pointers.has(e.pointerId)) return
    pointers.delete(e.pointerId)
    if (pointers.size || rejected || window.getSelection()?.toString()) return
    const dx = e.clientX - startX, dy = e.clientY - startY, settings = options()
    if (Math.abs(dx) > 80 && Math.abs(dx) > Math.abs(dy) * 2) {
      if (fingers === 2 && settings.swipeBooks) actions.book(dx < 0 ? 1 : -1)
      else if (fingers === 1 && settings.swipeChapters) actions.chapter(dx < 0 ? 1 : -1)
    } else if (!moved && fingers === 1 && settings.tapPaging && Date.now()-started<350) {
      const rect = element.getBoundingClientRect(), x = (e.clientX - rect.left) / rect.width
      if (x < .25 || x > .75) actions.page(x < .25 ? -1 : 1)
    }
  }
  function cancel() { pointers.clear(); rejected = true }
  element.addEventListener('pointerdown', down); element.addEventListener('pointermove', move); element.addEventListener('pointerup', up); element.addEventListener('pointercancel', cancel)
  return () => { element.removeEventListener('pointerdown', down); element.removeEventListener('pointermove', move); element.removeEventListener('pointerup', up); element.removeEventListener('pointercancel', cancel) }
}
export function pageReader(element: HTMLElement, direction: number) {
 const measured=measuredReaderLines(element)
 const distance=measuredPageDistance(measured.lines,measured.top,measured.bottom,direction)
 if(distance!==undefined)element.scrollBy({top:distance,behavior:'instant'})
 else {
  element.scrollBy({top:direction*(measured.bottom-measured.top),behavior:'instant'})
  const next=measuredReaderLines(element)
  element.scrollBy({top:firstReadingLineDistance(next.lines,next.top,next.bottom),behavior:'instant'})
 }
}
