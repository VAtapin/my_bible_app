export function verseTarget(value: unknown, numbers: number[]): number | undefined {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return undefined
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 && numbers.includes(number) ? number : undefined
}

export function createLongPress(open: () => void, delay = 550) {
  let timer: ReturnType<typeof setTimeout> | undefined
  let origin: { x: number; y: number } | undefined
  function cancel() { clearTimeout(timer); timer = undefined; origin = undefined }
  return {
    start(x: number, y: number) { cancel(); origin = { x, y }; timer = setTimeout(() => { timer = undefined; open() }, delay) },
    move(x: number, y: number) { if (origin && Math.hypot(x - origin.x, y - origin.y) > 10) cancel() },
    cancel,
  }
}
