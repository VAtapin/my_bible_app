import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {afterEach,it,expect,vi} from 'vitest'
import ContinuousReading from './ContinuousReading.vue'
import type {BibleChapter} from '@/api/contracts'
import type {ChapterService} from '@/services/chapterService'
import type {CalendarReadingFocus} from '@/services/calendarReadingFocus'
afterEach(()=>vi.unstubAllGlobals())
const source:BibleChapter={translation:{code:'actual',name:'Actual',short_name:null,language:{code:'ru',name:'Русский'}},book:{slug:'john',name:'John',short_name:null,chapters_count:10},chapter:{number:8,verses_count:4},verses:['John.3.1','John.3.2','John.3.3','John.4.1'].map((osis_ref,index)=>({id:index+1,number:Number(osis_ref.split('.')[2]),osis_ref,text:'Actual '+osis_ref,plain_text:'Actual '+osis_ref,has_strong_markup:false}))}
async function render(readingFocus?:CalendarReadingFocus){const storage={getItem:()=>null,setItem:()=>{}};vi.stubGlobal('window',{localStorage:storage});vi.stubGlobal('localStorage',storage);return renderToString(createSSRApp(ContinuousReading,{initial:source,service:{} as ChapterService,bookmarks:new Set(),readingFocus}))}
it('renders all source verses inside the multi-chapter focus and both boundaries, retaining the unselected surrounding context',async()=>{
 const html=await render({title:'John 3:2–4:1',passages:[{book:'John',start:{chapter:3,verse:2},end:{chapter:4,verse:1}}]})
 const rows=html.match(/<li\b[^>]*>/g)??[]
 expect(rows).toHaveLength(4);expect(rows[0]).not.toContain('reading-focus')
 expect(rows.slice(1).every(row=>row.includes('reading-focus'))).toBe(true)
 expect(html).toContain('Начало чтения');expect(html).toContain('Конец чтения');expect(html).toContain('Actual John.3.1')
})
it('a normal reader render has no retained day-reading highlight or boundary labels',async()=>{
 const html=await render();expect(html).not.toContain('reading-focus');expect(html).not.toContain('Начало чтения');expect(html).not.toContain('Конец чтения')
})
