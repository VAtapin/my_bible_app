import {describe,it,expect,vi} from 'vitest'
import {calendarReaderBack} from './calendarReaderBack'

function browser(){
 const listeners=new Set<()=>void>(),entries:unknown[]=[{position:4,current:'/calendar?date=2026-10-09',scroll:{top:381}}]
 const history={get state(){return entries.at(-1)},pushState:vi.fn((state:unknown)=>entries.push(state)),back:vi.fn(()=>{entries.pop();for(const listener of listeners)listener()})}
 const target={history,location:{href:'https://example.test/calendar?date=2026-10-09'},addEventListener:(_name:string,listener:()=>void)=>listeners.add(listener),removeEventListener:(_name:string,listener:()=>void)=>listeners.delete(listener)} as unknown as Pick<Window,'history'|'location'|'addEventListener'|'removeEventListener'>
 return{target,history,entries,listeners}
}
describe('local calendar text return',()=>{
 it('Close and browser Back remove only the reading entry while retaining the same date and saved scroll state',()=>{
   for(const closeWithButton of [true,false]){
     const{target,history,entries}=browser(),original=history.state,closed=vi.fn(),reader=calendarReaderBack(closed,target)
     expect(history.pushState).toHaveBeenCalledWith(expect.objectContaining({position:4,current:'/calendar?date=2026-10-09',scroll:{top:381}}),'',target.location.href)
     expect(entries).toHaveLength(2)
     if(closeWithButton)reader.close();else history.back()
     expect(closed).toHaveBeenCalledTimes(1);expect(history.state).toBe(original)
     reader.dispose();expect(history.back).toHaveBeenCalledTimes(1)
   }
 })
 it('unmount removes its own entry, but never goes Back from an unrelated new navigation',()=>{
   const first=browser(),reader=calendarReaderBack(vi.fn(),first.target)
   reader.dispose();expect(first.entries).toHaveLength(1);expect(first.listeners.size).toBe(0)
   const second=browser(),other=calendarReaderBack(vi.fn(),second.target)
   second.entries.push({current:'/more'})
   other.dispose();expect(second.history.back).not.toHaveBeenCalled();expect(second.listeners.size).toBe(0)
 })
})
