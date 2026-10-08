import { offlineStores, openOfflineDatabase, runRequest } from './database'

const prefix = 'calendar-media:'
export const previewMaxSide = 320
export const previewMaxBytes = 80 * 1024
export interface CalendarMediaCache {
  save(url: string, preview: boolean, signal?: AbortSignal): Promise<void>
  read(url: string): Promise<Blob | undefined>
}

export async function readCalendarState<T>(key: string): Promise<T | undefined> {
  const db = await openOfflineDatabase()
  try {
    const row = await runRequest<{ key: string; value: T } | undefined>(db.transaction(offlineStores.state).objectStore(offlineStores.state).get(key))
    return row?.value
  } finally { db.close() }
}
export async function writeCalendarState(key: string, value: unknown): Promise<void> {
  const db = await openOfflineDatabase()
  try {
    // A request's success is not a committed download (quota failures can abort later).
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(offlineStores.state, 'readwrite')
      transaction.oncomplete = () => resolve()
      transaction.onabort = transaction.onerror = () => reject(transaction.error ?? new Error('Storage failed'))
      transaction.objectStore(offlineStores.state).put({ key, value })
    })
  } finally { db.close() }
}

export function previewSize(width: number, height: number): { width: number; height: number } {
  const scale = Math.min(1, previewMaxSide / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

async function thumbnail(blob: Blob): Promise<Blob> {
  const url = URL.createObjectURL(blob)
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    const canvas = document.createElement('canvas')
    const size = previewSize(image.naturalWidth, image.naturalHeight)
    canvas.width = size.width; canvas.height = size.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Image conversion unavailable')
    context.drawImage(image, 0, 0, size.width, size.height)
    const result = await new Promise<Blob>((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('Image conversion failed')), 'image/webp', .72))
    if (result.size > previewMaxBytes) throw new Error('Preview too large')
    return result
  } finally { URL.revokeObjectURL(url) }
}

export function createCalendarMediaCache(fetcher = fetch, resize = thumbnail): CalendarMediaCache {
  return {
    read: (url) => readCalendarState<Blob>(`${prefix}${url}`),
    async save(url, preview, signal) {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      if (await this.read(url)) return
      const requestUrl = new URL(url)
      // Existing Bible Desktop image endpoint now supplies bounded previews.
      if (preview && /^\/api\/calendar\/icons\/\d+\/images\/\d+$/.test(requestUrl.pathname)) requestUrl.searchParams.set('preview', '1')
      const response = await fetcher(requestUrl.href, { credentials: 'omit', signal: AbortSignal.any([AbortSignal.timeout(15_000), ...(signal ? [signal] : [])]) })
      if (!response.ok) throw new Error(`Image HTTP ${response.status}`)
      if (Number(response.headers.get('Content-Length')) > 8 * 1024 * 1024) throw new Error('Image too large')
      const original = await response.blob()
      if (original.size > 8 * 1024 * 1024 || !original.type.startsWith('image/')) throw new Error('Invalid image')
      const stored = preview ? await resize(original) : original
      if (stored.size > previewMaxBytes) throw new Error('Asset too large')
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      await writeCalendarState(`${prefix}${url}`, stored)
    },
  }
}
