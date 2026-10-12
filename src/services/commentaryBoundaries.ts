import type { CommentaryEntry } from '@/api/study'
import type {BibleChapter} from '@/api/contracts'
import {ApiError} from '@/api/client'
export function finalBoundaryFailure(error:unknown):boolean{return error instanceof ApiError&&(error.kind==='invalid-response'||error.kind==='http'&&(error.status??0)>=400&&(error.status??0)<500&&error.status!==429)}
export interface CommentaryBoundary {book:boolean;chapterBoundary:boolean}
export function readerCommentaryBoundaries(chapters:readonly BibleChapter[]):Map<string,{start?:CommentaryBoundary;end?:CommentaryBoundary}>{
 const ordered=[...new Map(chapters.map(chapter=>[chapter.chapter.number,chapter])).values()].sort((a,b)=>a.chapter.number-b.chapter.number)
 const rows=ordered.flatMap(chapter=>chapter.verses.map(verse=>({chapter,verse}))),result=new Map<string,{start?:CommentaryBoundary;end?:CommentaryBoundary}>()
 rows.forEach(({chapter,verse},index)=>{
  const previous=rows[index-1],next=rows[index+1],osisChapter=verse.osis_ref.split('.').slice(0,2).join('.')
  const adjacent=(row:typeof next)=>!!row&&Math.abs(row.chapter.chapter.number-chapter.chapter.number)<=1
  const starts=verse.osis_ref.split('.')[2]==='1'||(adjacent(previous)&&previous!.verse.osis_ref.split('.').slice(0,2).join('.')!==osisChapter)
  const ends=adjacent(next)&&next!.verse.osis_ref.split('.').slice(0,2).join('.')!==osisChapter
  const bookStart=chapter.chapter.number===1&&verse.osis_ref===chapter.verses[0]?.osis_ref,bookEnd=chapter.chapter.number===chapter.book.chapters_count&&verse.osis_ref===chapter.verses.at(-1)?.osis_ref
  if(starts||ends||bookStart||bookEnd)result.set(verse.osis_ref,{start:starts||bookStart?{book:bookStart,chapterBoundary:starts}:undefined,end:ends||bookEnd?{book:bookEnd,chapterBoundary:ends||bookEnd}:undefined})
 })
 return result
}

export function generalCommentary(entry: CommentaryEntry): boolean {
  return entry.chapter_from === 0 || entry.verse_from === 0
}

/** Zero coordinates identify general material; titles never determine placement. */
export function boundaryCommentaries(entries: CommentaryEntry[], chapter: number, finalChapter: boolean, edge: 'start' | 'end',bookBeginning=chapter===1,chapterBoundary=true) {
  return [...new Map(entries.filter(entry => {
    if (!generalCommentary(entry)) return false
    const after = entry.placement === 'after'
    if (after !== (edge === 'end')) return false
    if (entry.chapter_from === 0) return after ? finalChapter : bookBeginning
    return chapterBoundary && chapter === (after ? entry.chapter_to ?? entry.chapter_from : entry.chapter_from)
  }).map(entry => [entry.id, entry])).values()]
}
