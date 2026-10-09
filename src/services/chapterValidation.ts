import type { BibleChapter } from '@/api/contracts'

export function validChapter(value: BibleChapter, code: string, slug: string, number: number): boolean {
  return value.translation.code === code && value.book.slug === slug && value.chapter.number === number
    && value.chapter.verses_count === value.verses.length
    && value.verses.every(verse => Number.isInteger(verse.number) && verse.number > 0 && Boolean(verse.osis_ref))
    && new Set(value.verses.map(verse => verse.osis_ref)).size === value.verses.length
    && new Set(value.verses.map(verse => verse.number)).size === value.verses.length
}
