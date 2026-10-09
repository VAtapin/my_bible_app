import { readFileSync } from 'node:fs'
export const socialPages = JSON.parse(readFileSync(new URL('../config/social-pages.json', import.meta.url), 'utf8'))
const escape = (value) => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')

export function localizedSocialPage(html, language, root = false) {
  const page = socialPages[language]
  if (!page) throw new Error('Unsupported language')
  const canonical = `https://bible-app.online/${root ? '' : language}`
  const image = `https://bible-app.online/brand/share-${language}.jpg`
  const tags = [
    `<link rel="canonical" href="${canonical}" />`,
    ...Object.entries({ 'og:type': 'website', 'og:site_name': 'Bible App', 'og:title': page.title, 'og:description': page.description, 'og:url': canonical, 'og:locale': page.locale, 'og:image': image, 'og:image:secure_url': image, 'og:image:type': 'image/jpeg', 'og:image:width': '1200', 'og:image:height': '630', 'og:image:alt': page.imageAlt }).map(([key, value]) => `<meta property="${key}" content="${escape(value)}" />`),
    ...Object.entries({ 'twitter:card': 'summary_large_image', 'twitter:title': page.title, 'twitter:description': page.description, 'twitter:image': image, 'twitter:image:alt': page.imageAlt }).map(([key, value]) => `<meta name="${key}" content="${escape(value)}" />`),
    ...Object.keys(socialPages).map((code) => `<link rel="alternate" hreflang="${code}" href="https://bible-app.online/${code}" />`),
    '<link rel="alternate" hreflang="x-default" href="https://bible-app.online/" />',
  ].join('\n    ')
  return html.replace(/<html lang="[^"]*">/, `<html lang="${language}">`)
    .replace(/<title>[^<]*<\/title>/, `<title>${escape(page.title)}</title>`)
    .replace(/<meta name="description" content="[^"]*"\s*\/>/, `<meta name="description" content="${escape(page.description)}" />`)
    .replace('</head>', `    ${tags}\n  </head>`)
}
