import {describe,it,expect} from 'vitest'
import {createSSRApp,h} from 'vue'
import {renderToString} from 'vue/server-renderer'
import type {AutomaticCalendarPlan} from '@/api/automaticCalendar'
import CalendarServiceTextsContent from './CalendarServiceTextsContent.vue'
const metadata={familyId:'work:actual',workSlug:'actual',language:'cu',orthography:'traditional',edition:'internal-edition-id',sourceKind:'liturgical-corpus',selectedTextId:17,contentHash:'a'.repeat(64),availableEditions:[],selection:'preferred',missingReason:null}
const render=(data:unknown)=>renderToString(createSSRApp({render:()=>h(CalendarServiceTextsContent,{plan:data as AutomaticCalendarPlan})}))
describe('daily calendar service texts',()=>{
 it('shows every full body immediately without opening controls or internal explanations',async()=>{
  const output=await render({assignments:[{...metadata,textId:17,slot:'troparion-of-day',title:'Харитону',text:'Полный тропарь\nВторая строка',insert:false}],expansions:[{...metadata,id:'expansion',title:'Кондак',text:'Полный кондак'}],properCoverage:{message:'Внутреннее пояснение о порядке на Часах'}})
  expect(output).toContain('Полный тропарь');expect(output).toContain('Вторая строка');expect(output).toContain('Полный кондак');expect(output).toContain('slavonic-unicode')
  for(const hidden of ['Редакция выбирается','Внутреннее пояснение','Справочный текст','internal-edition-id','<button','<details','<dialog'])expect(output).not.toContain(hidden)
 })
 it('retains an honest unavailable-text status without an open button',async()=>{
  const output=await render({assignments:[{...metadata,textId:17,slot:'troparion-of-day',title:'Тропарь',selection:'missing',text:'',language:null}],expansions:[]})
  expect(output).toContain('role="status"');expect(output).not.toContain('<button');expect(output).not.toContain('internal-edition-id')
 })
})