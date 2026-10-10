import {createSSRApp,type SSRContext} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {afterEach,it,expect,vi} from 'vitest'
import CalendarTextReader from './CalendarTextReader.vue'
import type {CalendarTextMetadata} from '@/api/automaticCalendar'
afterEach(()=>vi.unstubAllGlobals())
it('renders the complete local service text, actual script and Close inside the fullscreen reader',async()=>{
 const storage={getItem:()=>null,setItem:()=>{}};vi.stubGlobal('window',{localStorage:storage})
 const item:CalendarTextMetadata&{title:string;text:string}={title:'Actual service',text:'Сла́ва ѻ҆ц҃ꙋ̀\nПолный текст без обрезания',familyId:'work:actual',workSlug:'actual',language:'cu',orthography:'traditional',edition:'actual-edition',sourceKind:'liturgical-corpus',selectedTextId:17,contentHash:'a'.repeat(64),availableEditions:[],selection:'preferred',missingReason:null}
 const context:SSRContext={};await renderToString(createSSRApp(CalendarTextReader,{item}),context)
 const html=context.teleports?.body??''
 expect(html).toContain('calendar-text-reader');expect(html).toContain('Закрыть');expect(html).toContain('slavonic-unicode')
 expect(html).toContain('Полный текст без обрезания');expect(html).toContain('Сла́ва ѻ҆ц҃ꙋ̀');expect(html).not.toContain('href="/reader')
})
