import type { BibleChapter, BibleVerse } from '@/api/contracts'

export interface PassagePoint { chapter: number; verse: number }
export interface PassageVerse extends PassagePoint { osis: string; text: string }
export interface SavedPassage {
  translationCode: string; translationName: string; bookSlug: string; bookName: string
  start: PassagePoint; end: PassagePoint; verses: PassageVerse[]
}
export interface PassageFormat { reference: boolean; translation: boolean; numbers: boolean }
export interface StudyBookmark { id: string; passage: SavedPassage; title: string; description: string; color: string; collection: string; order: number }
export interface MemoryCard { id: string; passage: SavedPassage; learned: boolean }
export interface WordMark { id: string; code: string; osis: string; sourceText: string; start: number; end: number; quote: string; color: string; underline: boolean; note: string }
export interface StudyPalette { [name: string]: { day: string; night: string } }
export interface PersonalStudy { version: 1; bookmarks: StudyBookmark[]; cards: MemoryCard[]; marks: WordMark[]; palette: StudyPalette }
export const defaultPalette: StudyPalette = { yellow: { day: '#ffe49a', night: '#66521b' }, green: { day: '#b5e3bb', night: '#245737' }, blue: { day: '#b8d9fb', night: '#254f72' }, pink: { day: '#f7bfd8', night: '#702d51' } }
export const emptyPersonalStudy = (): PersonalStudy => ({ version: 1, bookmarks: [], cards: [], marks: [], palette: structuredClone(defaultPalette) })
export function passagePoint(value: string): PassagePoint {
  const match = /^(\d+):(\d+)$/.exec(value.trim())
  if (!match || !Number.isSafeInteger(Number(match[1])) || !Number.isSafeInteger(Number(match[2])) || Number(match[1]) < 1 || Number(match[2]) < 1) throw new Error('Invalid passage')
  return { chapter: Number(match[1]), verse: Number(match[2]) }
}
export const comparePoints = (a: PassagePoint, b: PassagePoint) => a.chapter - b.chapter || a.verse - b.verse
/** Every chapter is verified before committing the selection, including unavailable interior verses. */
export async function collectPassage(source: BibleChapter, start: PassagePoint, end: PassagePoint, load: (number: number) => Promise<BibleChapter>): Promise<SavedPassage> {
  if (comparePoints(start, end) > 0 || start.chapter < 1 || end.chapter > source.book.chapters_count || end.chapter - start.chapter > 200) throw new Error('Invalid passage')
  const verses: PassageVerse[] = []
  for (let number = start.chapter; number <= end.chapter; number++) {
    const chapter = number === source.chapter.number ? source : await load(number)
    if (chapter.translation.code !== source.translation.code || chapter.book.slug !== source.book.slug || chapter.chapter.number !== number) throw new Error('Wrong chapter')
    const first = number === start.chapter ? start.verse : 1
    const last = number === end.chapter ? end.verse : Math.max(0, ...chapter.verses.map(v => v.number))
    if (!chapter.verses.some(v => v.number === first) || !chapter.verses.some(v => v.number === last)) throw new Error('Missing boundary verse')
    const selected = chapter.verses.filter(v => v.number >= first && v.number <= last).sort((a, b) => a.number - b.number)
    if (selected.length !== last - first + 1 || selected.some(v => !v.plain_text.trim()) || new Set(selected.map(v => v.number)).size !== selected.length) throw new Error('Incomplete passage')
    verses.push(...selected.map(v => ({ chapter: number, verse: v.number, osis: v.osis_ref, text: v.plain_text })))
  }
  return { translationCode: source.translation.code, translationName: source.translation.short_name || source.translation.name, bookSlug: source.book.slug, bookName: source.book.name, start, end, verses }
}
export function passageReference(passage: SavedPassage): string {
  const { start, end } = passage
  return `${passage.bookName} ${start.chapter}:${start.verse}${comparePoints(start, end) === 0 ? '' : start.chapter === end.chapter ? `–${end.verse}` : `–${end.chapter}:${end.verse}`}`
}
export function formatPassage(passage: SavedPassage, format: PassageFormat): string {
  const body = passage.verses.map(v => `${format.numbers ? `${passage.start.chapter === passage.end.chapter ? v.verse : `${v.chapter}:${v.verse}`} ` : ''}${v.text}`).join('\n')
  return [body, format.reference ? passageReference(passage) : '', format.translation ? passage.translationName : ''].filter(Boolean).join('\n\n')
}
export function markMatches(mark: WordMark, code: string, verse: Pick<BibleVerse, 'osis_ref' | 'plain_text'>): boolean {
  return mark.code === code && mark.osis === verse.osis_ref && mark.sourceText === verse.plain_text && Number.isInteger(mark.start) && mark.start >= 0 && mark.end > mark.start && mark.end <= verse.plain_text.length && verse.plain_text.slice(mark.start, mark.end) === mark.quote
}
export function createWordMark(code: string, verse: BibleVerse, start: number, end: number, color: string, underline: boolean, note = ''): WordMark {
  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end <= start || end > verse.plain_text.length) throw new Error('Invalid word selection')
  return { id: crypto.randomUUID(), code, osis: verse.osis_ref, sourceText: verse.plain_text, start, end, quote: verse.plain_text.slice(start, end), color, underline, note }
}
/** Erasing a middle portion retains both sides; old text versions are never changed. */
export function eraseWordMarks(marks: WordMark[], code: string, verse: BibleVerse, start: number, end: number): WordMark[] {
  return marks.flatMap(mark => {
    if (!markMatches(mark, code, verse) || mark.end <= start || mark.start >= end) return [mark]
    const pieces: WordMark[] = []
    if (mark.start < start) pieces.push({ ...mark, end: start, quote: verse.plain_text.slice(mark.start, start) })
    if (mark.end > end) pieces.push({ ...mark, id: crypto.randomUUID(), start: end, quote: verse.plain_text.slice(end, mark.end) })
    return pieces
  })
}
export function wordSegments(text: string, code: string, osis: string, marks: WordMark[]) {
  const valid = marks.filter(mark => markMatches(mark, code, { osis_ref: osis, plain_text: text }))
  const boundaries = [...new Set([0, text.length, ...valid.flatMap(m => [m.start, m.end])])].sort((a, b) => a - b)
  return boundaries.slice(0, -1).map((start, index) => ({ start, text: text.slice(start, boundaries[index + 1]), marks: valid.filter(m => m.start <= start && m.end >= boundaries[index + 1]!) }))
}
/** Updating a note never relocates its source anchor or overwrites a concurrently changed note. */
export function updateWordNote(value:PersonalStudy,expected:WordMark,note:string):PersonalStudy {
 const current=value.marks.find(mark=>mark.id===expected.id)
 if(!current||current.code!==expected.code||current.osis!==expected.osis||current.sourceText!==expected.sourceText||current.start!==expected.start||current.end!==expected.end||current.quote!==expected.quote||current.note!==expected.note)throw Error('Word note changed')
 return {...value,marks:value.marks.map(mark=>mark.id===current.id?{...mark,note}:mark)}
}
export function trimWordSelection(text:string,start:number,end:number):{start:number;end:number}|undefined {
 if(!Number.isInteger(start)||!Number.isInteger(end))return
 let first=Math.min(start,end),last=Math.max(start,end)
 if(first<0||last>text.length)return
 while(first<last&&/\s/u.test(text[first]!))first++
 while(last>first&&/\s/u.test(text[last-1]!))last--
 return first<last?{start:first,end:last}:undefined
}
