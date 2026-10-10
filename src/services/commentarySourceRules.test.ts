import { afterEach,expect,it,vi } from 'vitest'
import { readCommentarySourceRules,writeCommentarySourceRules,sourcesAtOsis,sourceRuleSegments,loadRuleCommentaries,type CommentarySourceRule } from './commentarySourceRules'
const rules:CommentarySourceRule[]=[{id:'older',book:'John',first:{chapter:3,verse:2},last:{chapter:3,verse:5},sources:['A']},{id:'newer',book:'John',first:{chapter:3,verse:4},last:{chapter:4,verse:2},sources:['B']}]
afterEach(()=>vi.unstubAllGlobals())
it('uses the latest covering interval for each canonical verse and restores the book/global choice outside it',()=>{
 expect(sourcesAtOsis('John.3.1',['BOOK'],rules)).toEqual(['BOOK']);expect(sourcesAtOsis('John.3.2',['BOOK'],rules)).toEqual(['A']);expect(sourcesAtOsis('John.3.4',['BOOK'],rules)).toEqual(['B']);expect(sourcesAtOsis('John.4.2',['BOOK'],rules)).toEqual(['B']);expect(sourcesAtOsis('John.4.3',['BOOK'],rules)).toEqual(['BOOK']);expect(sourcesAtOsis('Acts.3.4',['GLOBAL'],rules)).toEqual(['GLOBAL'])
 const parts=sourceRuleSegments('John',{chapter:3,verse:1},{chapter:4,verse:3},['BOOK'],rules)
 expect(parts.find(p=>p.first.verse===4&&p.first.chapter===3)?.sources).toEqual(['B'])
})
it('persists real interval rules and validates corrupt saved data',()=>{
 const values=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value)});const dispatch=vi.fn();vi.stubGlobal('window',{dispatchEvent:dispatch})
 writeCommentarySourceRules(rules);expect(readCommentarySourceRules()).toEqual(rules);expect(dispatch).toHaveBeenCalledOnce();values.set('bible-desktop:commentary-range-sources','[{"id":"bad","book":"John"}]');expect(readCommentarySourceRules()).toEqual([])
})
it('requests each interval only with its sources and excludes articles from foreign sources',async()=>{
 const fetch=vi.fn(async(_chapter:number,_offset:number,sources:string[])=>({total:2,entries:[{id:sources[0]==='A'?1:2,module_code:sources[0]!,chapter_from:3,verse_from:1,chapter_to:3,verse_to:10},{id:999,module_code:'FOREIGN',chapter_from:3,verse_from:1,chapter_to:3,verse_to:10}]} as never))
 const entries=await loadRuleCommentaries('John',{chapter:3,verse:2},{chapter:3,verse:5},['BOOK'],rules,fetch)
 expect(entries.map(e=>e.id)).toEqual([1,2]);expect(fetch.mock.calls.map(call=>call[2])).toEqual([['A'],['B']])
})
