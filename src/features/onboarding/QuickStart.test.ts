import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import SetupView from './SetupView.vue'
import { createCompleteConfiguration, isAppConfiguration, prayerContentIds, sectionIds } from '@/profile/configuration'
import { defaultBibleTranslations, interfaceLanguageIds } from '@/i18n/locale'
import { getMessages } from '@/i18n'

describe('one-click complete setup', () => {
  it.each(interfaceLanguageIds)('enables all modules and prayer collections in %s without requiring online setup', (language) => {
    const configuration = createCompleteConfiguration(language, new Date('2026-10-08T12:00:00Z'))
    expect(isAppConfiguration(configuration)).toBe(true)
    expect(configuration.sections).toEqual([...sectionIds])
    for (const content of prayerContentIds) expect(configuration.prayers[content]).toBe(true)
    expect(configuration.bible.translationCode).toBe(defaultBibleTranslations[language])
    expect(configuration.interfaceLanguage).toBe(language)
    expect(configuration.calendar.level).toBe('all')
    expect(configuration.education).toEqual({ pluginIds: ['azbuka'], showClock: true, showProgress: true })
    expect(configuration.prayers.languageCodes).toContain('cu')
    expect(configuration.prayers.languageCodes).toContain('cu-civil')
    expect(configuration.notifications.enabled).toBe(false)
  })
  it('does not show the selection wizard on the quick-start route', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/setup/quick', component: { render: () => h('div') } }] })
    await router.push('/setup/quick')
    const html = await renderToString(createSSRApp(SetupView, { mode: 'quick' }).use(createPinia()).use(router))
    expect(html).not.toContain('class="setup-heading"')
    expect(html).not.toContain('class="language-selector"')
    expect(html).not.toContain('class="step-indicator"')
    expect(html).toContain('role="status"')
  })
  it.each(interfaceLanguageIds)('explains the one-tap behavior on the %s start page', (language) => {
    expect(getMessages(language).welcome.quickDescription).not.toBe('')
  })
})
