import type { BibleChapter } from '@/api/contracts'

export function validateContinuation(initial: BibleChapter, value: BibleChapter, number: number): BibleChapter {
  if (value.translation.code !== initial.translation.code || value.book.slug !== initial.book.slug || value.chapter.number !== number)
    throw new Error('Wrong continuation identity')
  const osis = initial.verses[0]?.osis_ref.split('.')[0] ?? value.verses[0]?.osis_ref.split('.')[0]
  if (value.verses.some(v => v.osis_ref !== `${osis}.${number}.${v.number}`) || new Set(value.verses.map(v => v.osis_ref)).size !== value.verses.length)
    throw new Error('Wrong continuation references')
  return value
}
