import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import DayIcon from './DayIcon.vue'
const styles = readFileSync(new URL('../themes.css', import.meta.url), 'utf8')

describe('full size icon dialog', () => {
  it('separates the large dialog image from the card thumbnail', async () => {
    const html = await renderToString(createSSRApp(DayIcon, { icon: { id: 1, title: 'Icon', image_url: '/icon.png' } }))
    expect(html).toContain('class="icon-full-image"')
    expect(html).toContain('class="day-icon-button"')
    expect(styles).toContain('.icon-dialog .icon-viewer > .icon-full-image')
    expect(styles).toContain('height: min(68dvh, 820px)')
    expect(styles).toContain('width: min(960px, 94vw)')
  })
})
