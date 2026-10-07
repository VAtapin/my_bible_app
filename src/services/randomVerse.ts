import type { BibleChapter } from '@/api/contracts'
import type { ChapterService } from './chapterService'

export function chooseVerse(chapter: BibleChapter, random = Math.random) {
  const available = chapter.verses.filter((verse) => verse.plain_text.trim())
  const short = available.filter((verse) => verse.plain_text.length <= 220)
  const verses = short.length ? short : available
  if (!verses.length) return undefined
  const verse = verses[Math.min(verses.length - 1, Math.floor(random() * verses.length))]!
  return {
    text: verse.plain_text,
    reference: `${chapter.book.short_name?.split(/[\s,;|]+/)[0] || chapter.book.name} ${chapter.chapter.number}:${verse.number}`,
    route: { path: '/reader', query: { translation: chapter.translation.code, book: chapter.book.slug, chapter: String(chapter.chapter.number) } },
  }
}

export async function loadRandomVerse(service: ChapterService, translation: string, random = Math.random) {
  const passages = [{ book: 'psalms', chapter: 22 }, { book: 'john', chapter: 1 }, { book: 'matthew', chapter: 6 }, { book: '1-corinthians', chapter: 13 }]
  const target = passages[Math.min(passages.length - 1, Math.floor(random() * passages.length))]!
  let chapter = await service.readOffline(translation, target.book, target.chapter)
  if (!chapter) {
    try { chapter = await service.download(translation, target.book, target.chapter) }
    catch { chapter = (await service.listStored()).find((item) => item.data.translation.code === translation)?.data }
  }
  return chapter ? chooseVerse(chapter, random) : undefined
}
