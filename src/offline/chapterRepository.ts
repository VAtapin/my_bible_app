import type { BibleChapter } from '@/api/contracts'

export interface StoredChapter {
  key: string
  savedAt: string
  data: BibleChapter
}

export interface ChapterRepository {
  get(key: string): Promise<StoredChapter | undefined>
  put(chapter: StoredChapter): Promise<void>
  list(): Promise<StoredChapter[]>
  delete(key: string): Promise<void>
}

export function chapterKey(translationCode: string, bookSlug: string, chapter: number): string {
  return `${translationCode}:${bookSlug}:${chapter}`
}
