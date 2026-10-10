import{describe,it,expect,vi}from'vitest'
import{attachReaderGestures}from'./readerGestures'
describe('reader paging controls',()=>{
 it('does not page when source footnote, detail toggle or other control is activated',()=>{
  class Target {constructor(private selector:string){}closest(selector:string){return selector.includes(this.selector)?this:null}}
  vi.stubGlobal('Element',Target);vi.stubGlobal('window',{getSelection:()=>({toString:()=>''})})
  const handlers=new Map<string,(event:PointerEvent)=>void>()
  const element={addEventListener:(name:string,handler:(event:PointerEvent)=>void)=>handlers.set(name,handler),removeEventListener:(name:string)=>handlers.delete(name),getBoundingClientRect:()=>({left:0,width:100})} as unknown as HTMLElement
  const page=vi.fn(),dispose=attachReaderGestures(element,()=>({tapPaging:true,swipeBooks:true,swipeChapters:true}),{page,chapter:vi.fn(),book:vi.fn()})
  try{
   for(const selector of ['[role="button"]','summary','a','button:not(.verse-text)']){
    const event={pointerId:1,clientX:90,clientY:20,target:new Target(selector)} as unknown as PointerEvent
    handlers.get('pointerdown')!(event);handlers.get('pointerup')!(event)
   }
   expect(page).not.toHaveBeenCalled()
   const event={pointerId:1,clientX:90,clientY:20,target:new Target('verse-body')} as unknown as PointerEvent
   handlers.get('pointerdown')!(event);handlers.get('pointerup')!(event)
   expect(page).toHaveBeenCalledWith(1)
  }finally{dispose();vi.unstubAllGlobals()}
 })
})
