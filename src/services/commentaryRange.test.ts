import { describe, expect, it } from 'vitest'
import { studyPosition, overlapsStudyRange, loadCommentaryRange } from './commentaryRange'
import type { CommentaryEntry } from '@/api/study'
const article = (id: number, start: number, first: number, end: number | null, last: number | null) => ({ id, chapter_from: start, verse_from: first, chapter_to: end, verse_to: last } as CommentaryEntry)
describe('commentary ranges across chapters', () => {
 it('parses chapter:verse and single verse without guessing a book', () => { expect(studyPosition('4:2', 3)).toEqual({ chapter: 4, verse: 2 }); expect(studyPosition('2', 3)).toEqual({ chapter: 3, verse: 2 }); for(const invalid of ['0','0:2','3:0','1:2:3','-2']) expect(studyPosition(invalid, 3)).toBeUndefined() })
 it('uses canonical interval overlap including introductions', () => { const first = { chapter: 3, verse: 16 }, last = { chapter: 4, verse: 2 }; expect(overlapsStudyRange(article(1,3,15,4,1),first,last)).toBe(true); expect(overlapsStudyRange(article(1,4,3,null,3),first,last)).toBe(false); expect(overlapsStudyRange(article(1,4,0,null,null),first,last)).toBe(true); expect(overlapsStudyRange(article(1,0,0,null,null),first,last)).toBe(true) })
 it('reads every chapter page and shows a spanning article once', async () => { const calls: string[] = []; const spanning = article(1,3,15,4,1); const data = await loadCommentaryRange({chapter:3,verse:16},{chapter:4,verse:2},async(chapter,offset)=>{calls.push(`${chapter}:${offset}`); return {entries:offset === 0 ? [spanning] : [article(chapter, chapter, 0, null, null)],total:2} }); expect(calls).toEqual(['3:0','3:1','4:0','4:1']); expect(data.map(x=>x.id)).toEqual([1,3,4]) })
})
