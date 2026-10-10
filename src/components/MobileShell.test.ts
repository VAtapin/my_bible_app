import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createPinia} from 'pinia'
import {createMemoryHistory,createRouter} from 'vue-router'
import {afterEach,describe,it,expect,vi} from 'vitest'
import MobileShell from './MobileShell.vue'

describe('reading-only shell layout',()=>{
 afterEach(()=>vi.unstubAllGlobals())
 async function render(reading=false,readingViewport=false,showHeader=true){
   vi.stubGlobal('navigator',{onLine:true});vi.stubGlobal('window',{localStorage:{getItem:()=>null,setItem:()=>{}}})
   const router=createRouter({history:createMemoryHistory(),routes:['/','/today','/reader','/prayers','/calendar','/more'].map(path=>({path,component:{render:()=>null}}))})
   await router.push('/reader');await router.isReady()
   return renderToString(createSSRApp(MobileShell,{reading,readingViewport,showHeader,backTo:'/today'}).use(createPinia()).use(router))
 }
 it('opts only reading screens into full-width viewport while retaining header, Back and bottom navigation',async()=>{
   const html=await render(true,true)
   expect(html).toContain('reading-screen reading-viewport')
   expect(html).toContain('class="app-header"');expect(html).toContain('class="back-link"');expect(html).toContain('class="bottom-nav"')
 })
 it('keeps catalogue pages out of reading-specific width and scroll rules',async()=>{
   const html=await render()
   expect(html).not.toContain('reading-screen');expect(html).not.toContain('reading-viewport')
 })
 it('lets the reader use its own compact header without branding or connection status',async()=>{
   const html=await render(true,true,false)
   expect(html).toContain('without-header')
   expect(html).not.toContain('class="app-header"')
   expect(html).not.toContain('class="connection"')
   expect(html).toContain('class="bottom-nav"')
 })
})
