import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import ReaderToolbar from './ReaderToolbar.vue'
describe('reader toolbar', () => {
  it('starts closed with an accessible icon trigger and leaves tools out of the tab order', async () => {
    const html=await renderToString(createSSRApp(ReaderToolbar,{label:'Tools',closeLabel:'Close',actions:[{id:'search',label:'Search',icon:'search',to:'/search'}]}))
    expect(html).toContain('aria-label="Tools"');expect(html).toContain('aria-expanded="false"');expect(html).toContain('aria-controls=')
    expect(html).toContain('aria-hidden="true"');expect(html).not.toContain('<nav');expect(html).not.toContain('aria-label="Search"')
  })
})
