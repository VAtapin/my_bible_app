import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WelcomeView from './WelcomeView.vue'
import MobileShell from '@/components/MobileShell.vue'
import { getMessages, setInterfaceLanguage } from '@/i18n'
vi.mock('@/api', () => ({ bibleApi: {}, kalendarApi: { getMonth: vi.fn(async () => []), getCalendarDay: vi.fn(async () => { throw new Error('Offline fixture') }) } }))

beforeEach(() => {
  vi.stubGlobal('navigator', { onLine: true })
  vi.stubGlobal('window', { localStorage: { getItem: () => null, setItem: () => undefined } })
  vi.stubGlobal('document', { documentElement: { lang: '' }, querySelector: () => null })
  setInterfaceLanguage('ru')
})
afterEach(() => vi.unstubAllGlobals())

async function render(component: typeof WelcomeView | typeof MobileShell, props = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes: ['/', '/ru', '/de', '/uk', '/en', '/today', '/reader', '/prayers', '/calendar', '/more', '/setup/quick', '/setup/manual', '/restore', '/privacy'].map((path) => ({ path, component: { render: () => h('div') } })) })
  await router.push('/')
  await router.isReady()
  return renderToString(createSSRApp(component, props).use(createPinia()).use(router))
}

describe('first launch layout', () => {
  it.each(['ru', 'de', 'uk', 'en'] as const)('renders the reference composition and all three existing entry points in %s', async (language) => {
    setInterfaceLanguage(language)
    const html = await render(WelcomeView), text = getMessages(language)
    expect(html).toContain('welcome-picture')
    expect(html).toContain('/brand/welcome-church.png')
    expect(html).not.toContain('/brand/app-icon-512.png')
    expect(html).toContain('Bible Desktop</h1>')
    expect(html).toContain(text.welcome.intro)
    expect(html.match(/class="choice-card welcome-choice/g)).toHaveLength(3)
    for (const path of ['/setup/quick', '/setup/manual', '/restore', '/privacy']) expect(html).toContain(`href="${path}"`)
    expect(html).toContain(text.welcome.restoreDescription)
    expect(html).toContain(text.welcome.verseReference)
    expect(html).not.toContain('class="app-header"')
    expect(html).not.toContain('class="bottom-nav"')
    expect(html).not.toContain('choice-number')
    expect(html).toContain('welcome-calendar')
    for (const name of ['Русский', 'Deutsch', 'Українська', 'English']) expect(html).toContain(name)
    expect(html.indexOf('welcome-languages')).toBeLessThan(html.indexOf('welcome-hero'))
  })
  it('keeps the normal header and navigation enabled on other application screens', async () => {
    const html = await render(MobileShell)
    expect(html).toContain('class="app-header"')
    expect(html).toContain('class="bottom-nav"')
  })
  it('keeps a single image and the portrait reading order before the privacy link', async () => {
    const html = await render(WelcomeView)
    const positions = ['class="welcome-hero"', 'class="welcome-actions"', 'class="welcome-quote"', 'class="privacy-link"'].map((selector) => html.indexOf(selector))
    expect(positions.every((position) => position >= 0)).toBe(true)
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
    expect(html.match(/<img /g)).toHaveLength(1)
  })
  it.each([
    { showHeader: true, showNavigation: false },
    { showHeader: false, showNavigation: true },
    { showHeader: false, showNavigation: false },
  ])('reserves only the visible shell rows for %j', async (props) => {
    const html = await render(MobileShell, props)
    expect(html.includes('without-header')).toBe(!props.showHeader)
    expect(html.includes('without-navigation')).toBe(!props.showNavigation)
    expect(html.includes('class="app-header"')).toBe(props.showHeader)
    expect(html.includes('class="bottom-nav"')).toBe(props.showNavigation)
  })
})
