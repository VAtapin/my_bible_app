import type { CommentaryEntry } from '@/api/study'
export interface StudyPosition { chapter: number; verse: number }
export function studyPosition(value: string, chapter: number): StudyPosition | undefined {
 const match = /^(?:(\d+):)?(\d+)$/.exec(value.trim()); if (!match) return undefined
 const position = { chapter: match[1] ? Number(match[1]) : chapter, verse: Number(match[2]) }
 return Number.isSafeInteger(position.chapter) && Number.isSafeInteger(position.verse) && position.chapter > 0 && position.verse > 0 ? position : undefined
}
export function positionCompare(a: StudyPosition, b: StudyPosition) { return a.chapter - b.chapter || a.verse - b.verse }
export function canonicalStudyPosition(osis:string):StudyPosition { const parts=/^[A-Za-z0-9]+\.(\d+)\.(\d+)$/.exec(osis);if(!parts)throw new Error('Canonical verse identity unavailable');return{chapter:Number(parts[1]),verse:Number(parts[2])} }
export function overlapsStudyRange(entry: CommentaryEntry, first: StudyPosition, last: StudyPosition) {
 if (entry.chapter_from === 0) return true
 const start = { chapter: entry.chapter_from, verse: entry.verse_from || 0 }
 const end = { chapter: entry.chapter_to ?? entry.chapter_from, verse: entry.verse_from === 0 ? Number.MAX_SAFE_INTEGER : entry.verse_to ?? (entry.chapter_to === null ? entry.verse_from : Number.MAX_SAFE_INTEGER) }
 return positionCompare(start, last) <= 0 && positionCompare(end, first) >= 0
}
/** Traverse actual chapter pages; dedup by article identity, never by text. */
export async function loadCommentaryRange(first: StudyPosition, last: StudyPosition, fetch: (chapter: number, offset: number) => Promise<{ entries: CommentaryEntry[]; total: number }>, stale: () => boolean = () => false) {
 if (positionCompare(first, last) > 0) throw new Error('Invalid range')
 const entries = new Map<number, CommentaryEntry>()
 for (let chapter = first.chapter; chapter <= last.chapter && !stale(); chapter++) { let offset = 0; while (!stale()) { const page = await fetch(chapter, offset); for (const entry of page.entries) if (overlapsStudyRange(entry, first, last)) entries.set(entry.id, entry); offset += page.entries.length; if (!page.entries.length || offset >= page.total) break } }
 return [...entries.values()]
}
