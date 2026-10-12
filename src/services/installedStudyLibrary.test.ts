import {expect,it,vi} from 'vitest'
import {installedCommentaries} from './installedStudyLibrary'
vi.mock('./studyPackages',()=>({installedStudyPackages:async()=>[{manifest:{id:'SOURCE',kind:'commentary'},metadata:{name:'Source'}}]}))
vi.mock('./studyPackageLookup',()=>({studyPackageRows:async()=>({rows:[
 ...Array.from({length:12},(_,index)=>({api_id:index+1,chapter_from:1,verse_from:index+1,body:'Verse',title:'Verse'})),
 {api_id:20,chapter_from:1,verse_from:0,body:'Full conclusion',title:'Conclusion',placement:'after',chapter_to:null,verse_to:null},
 {api_id:21,chapter_from:0,verse_from:0,body:'Whole book introduction',title:'Introduction',chapter_to:null,verse_to:null},
],total:14})}))
it('filters installed general material before pagination and preserves explicit source placement',async()=>{
 const page=await installedCommentaries('genesis',1,['SOURCE'],0,'general')
 expect(page?.total).toBe(2)
 expect(page?.entries.map(entry=>entry.id)).toEqual([20,21])
 expect(page?.entries[0]).toMatchObject({body:'Full conclusion',placement:'after'})
 expect(page?.entries[1]?.placement).toBeNull()
 expect(await installedCommentaries('genesis',1,['SOURCE'],10,'general')).toMatchObject({entries:[],total:2})
 expect((await installedCommentaries('genesis',1,['SOURCE'],0))?.total).toBe(14)
})
