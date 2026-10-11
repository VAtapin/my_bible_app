import type { BibleVerse } from '@/api/contracts'

/** Source markers can touch the following word. Keep original UTF-16 anchors. */
export function remainingStrongRanges(verse: BibleVerse) {
  if (!verse.has_strong_markup) return []
  return [...verse.plain_text.matchAll(/(?<![\p{L}\p{N}])([HG])(\d{1,5})(?!\p{N})/giu)]
    .filter(match => Number(match[2]) > 0)
    .map(match => ({ start: match.index!, end: match.index! + match[0].length, number: `${match[1]!.toUpperCase()}${Number(match[2])}` }))
}
