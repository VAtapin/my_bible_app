import {expect,it} from 'vitest'
import type {BibleChapter} from '@/api/contracts'
import {studyContext} from './studyContext'
const previous={chapter:{number:108}} as BibleChapter
const selected={chapter:{number:109}} as BibleChapter
it('keeps commentary, dictionary and references on the selected verse despite previous visible chapter',()=>{
 expect(studyContext(selected,2,previous,selected,31,31)).toEqual({chapter:selected,first:2,last:2})
})
it('follows the visible range when study is opened from the toolbar without an explicit verse',()=>{
 expect(studyContext(undefined,undefined,previous,selected,30,31)).toEqual({chapter:previous,first:30,last:31})
 expect(studyContext(undefined,undefined,undefined,selected,1,2).chapter).toBe(selected)
})
