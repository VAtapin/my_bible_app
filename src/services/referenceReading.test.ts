import {it,expect,vi} from 'vitest'
import {readReferenceText} from './referenceReading'
import type {BibleChapter} from '@/api/contracts'
import type {ReferenceGroup} from './verseStudy'
import type {ChapterService} from './chapterService'
const group={targets:[{osis_ref:'Acts.6.13'}]} as ReferenceGroup
const service={} as ChapterService
const chapter={translation:{code:'RST'},book:{slug:'actual-acts'},chapter:{number:7},verses:[{id:80,number:5,osis_ref:'Acts.6.13',plain_text:'Реальный текст из установленного перевода.'}]} as BibleChapter
const locations=async()=>[{verse_id:80,verse_number:5,chapter_number:7,book_slug:'actual-acts',osis_ref:'Acts.6.13'}]
it('loads the exact source location even when module chapter numbers differ',async()=>{
 const read=vi.fn(async()=>chapter)
 expect(await readReferenceText(group,'RST',service,locations,read)).toEqual({'Acts.6.13':'Реальный текст из установленного перевода.'})
 expect(read).toHaveBeenCalledWith(service,'RST','actual-acts',7)
})
it('rejects a substituted edition, ID or reference instead of exposing the wrong text',async()=>{
 for(const value of [{...chapter,translation:{...chapter.translation,code:'OTHER'}},{...chapter,verses:[{...chapter.verses[0]!,id:90}]}])await expect(readReferenceText(group,'RST',service,locations,async()=>value)).rejects.toThrow()
 await expect(readReferenceText(group,'RST',service,async()=>[{...(await locations())[0]!,osis_ref:'Acts.6.14'}],async()=>chapter)).rejects.toThrow()
})
