import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {afterEach,describe,it,expect,vi} from 'vitest'
import ReaderSettings from './ReaderSettings.vue'

describe('compact reader controls',()=>{
 afterEach(()=>vi.unstubAllGlobals())
 it('puts each checkbox and its text in one clickable row, apart from two labelled sliders',async()=>{
   const storage={getItem:()=>null,setItem:vi.fn()}
   vi.stubGlobal('window',{localStorage:storage});vi.stubGlobal('localStorage',storage)
   const html=await renderToString(createSSRApp(ReaderSettings))
   const rows=html.match(/<label[^>]*class="switch-row"[^>]*>.*?<\/label>/g)??[]
   expect(rows).toHaveLength(15)
   for(const row of rows){expect(row).toMatch(/<input[^>]*type="checkbox"[^>]*><span[^>]*>[^<]+<\/span>/);expect(row).not.toContain('<br')}
   expect(html.match(/class="slider-row"/g)).toHaveLength(2)
   expect(html).toContain('class="switch-grid"')
 })
})
