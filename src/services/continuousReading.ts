import type { BibleChapter } from '@/api/contracts'
import type { ChapterService } from './chapterService'
import { readWebChapter } from './webBibleLibrary'
import type { BibleApi } from '@/api/client'
import { loadComparisonChapters } from './bibleComparison'

/** A missing target chapter is explicit; failed requests never masquerade as missing verses. */
export async function loadPairedContinuation(primary: BibleChapter, secondary: BibleChapter, number: number, service: ChapterService, api?:BibleApi) {
  if (!Number.isInteger(number) || number<1 || number>primary.book.chapters_count) throw new Error('Wrong continuation chapter')
  const first=primary.verses[0]?.osis_ref.split('.')[0], second=secondary.verses[0]?.osis_ref.split('.')[0]
  if(first&&second&&first!==second)throw new Error('Different canonical books')
  const value=validateContinuation(primary,await readWebChapter(service,primary.translation.code,primary.book.slug,number),number)
  let others:BibleChapter[]=[]
  let other:BibleChapter|undefined
  if(primary.translation.code===secondary.translation.code){other=value;others=[value]}
  else if(api?.getVerseLocations){try{others=await loadComparisonChapters(value,secondary.translation.code,api,service);other=others[0]}catch(error){if(!(error instanceof Error)||error.message!=='Exact comparison unavailable')throw error}}
  else if(number<=secondary.book.chapters_count){other=validateContinuation(secondary,await readWebChapter(service,secondary.translation.code,secondary.book.slug,number),number);others=[other]}
  return {primary:value,secondary:other,secondaryChapters:others}
}

export function validateContinuation(initial: BibleChapter, value: BibleChapter, number: number): BibleChapter {
  if (value.translation.code !== initial.translation.code || value.book.slug !== initial.book.slug || value.chapter.number !== number)
    throw new Error('Wrong continuation identity')
  const osis = initial.verses[0]?.osis_ref.split('.')[0] ?? value.verses[0]?.osis_ref.split('.')[0]
  if (value.verses.some(v => !new RegExp(`^${osis}\\.[1-9]\\d*\\.${v.number}$`,'u').test(v.osis_ref)) || new Set(value.verses.map(v => v.osis_ref)).size !== value.verses.length)
    throw new Error('Wrong continuation references')
  return value
}
