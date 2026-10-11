import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createPinia} from 'pinia'
import {createMemoryHistory,createRouter} from 'vue-router'
import {afterEach,expect,it,vi} from 'vitest'
import MoreView from './MoreView.vue'

afterEach(()=>vi.unstubAllGlobals())
it('shows all interface languages and main settings before the study catalogue',async()=>{
 const storage={getItem:()=>null,setItem:()=>{}}
 vi.stubGlobal('navigator',{onLine:true});vi.stubGlobal('window',{localStorage:storage});vi.stubGlobal('localStorage',storage)
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>null}}]})
 await router.push('/more');await router.isReady()
 const html=await renderToString(createSSRApp(MoreView).use(createPinia()).use(router))
 expect(html).toContain('role="group" aria-label="Язык приложения"')
 for(const code of ['ru','de','uk','en'])expect(html).toContain(`lang="${code}"`)
 expect(html.indexOf('class="settings-language"')).toBeLessThan(html.indexOf('href="/setup/manual?edit=1"'))
 expect(html.indexOf('href="/setup/manual?edit=1"')).toBeLessThan(html.indexOf('href="/help"'))
 expect(html.indexOf('class="appearance-options"')).toBeLessThan(html.indexOf('href="/help"'))
})
