import { describe, expect, it } from 'vitest'
// @ts-expect-error The build utility is a Node ES module, not runtime application code.
import { localizedSocialPage, socialPages } from '../../scripts/social-metadata.mjs'
describe('static localized messenger previews', () => {
  const html = '<html lang="ru"><head><meta name="description" content="Bible Desktop" /><title>Bible Desktop</title></head><body><script src="/assets/app.js"></script></body></html>'
  it.each(['ru', 'de', 'uk', 'en'])('publishes crawler-readable metadata for /%s without JavaScript', (language) => {
    const page = localizedSocialPage(html, language)
    expect(page).toContain(`<html lang="${language}">`)
    expect(page).toContain(`https://bible-app.online/${language}`)
    expect(page).toContain(`share-${language}.jpg`)
    expect(page).toContain('summary_large_image')
    expect(page).toContain('content="1200"')
    expect(page).toContain(socialPages[language].description)
    expect(page).toContain('/assets/app.js')
  })
})
