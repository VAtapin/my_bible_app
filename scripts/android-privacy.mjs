import { readFileSync } from 'node:fs'

export const androidPrivacy = JSON.parse(readFileSync(new URL('../config/android-privacy.json', import.meta.url), 'utf8'))
const escape = (value) => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;')

/** Complete static text for store reviewers/crawlers, independent of JavaScript and app setup. */
export function androidPrivacyHtml(language) {
  const text = androidPrivacy.locales[language]
  if (!text) throw new Error('Unknown Android privacy language')
  const links = Object.keys(androidPrivacy.locales).map((id) => `<a href="/android/privacy/${id}.html" lang="${id}"${id === language ? ' aria-current="page"' : ''}>${{ ru: 'Русский', de: 'Deutsch', uk: 'Українська', en: 'English' }[id]}</a>`).join(' ')
  return `<!doctype html>
<html lang="${language}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(text.title)}</title>
<link rel="canonical" href="https://bible-app.online/android/privacy/${language}.html">
<style>body{margin:0;background:#faf8f3;color:#17354b;font:17px/1.65 system-ui,sans-serif}main{max-width:820px;margin:auto;padding:32px 24px 64px}nav{display:flex;flex-wrap:wrap;gap:16px}a{color:#315b7a;overflow-wrap:anywhere}[aria-current]{font-weight:700}h1{font:700 clamp(26px,5vw,36px)/1.25 Georgia,serif;margin:28px 0 16px}h2{font-size:21px;margin:28px 0 8px}p{margin:8px 0}footer{border-top:1px solid #d3dde4;margin-top:32px;padding-top:16px}</style></head>
<body><main><nav aria-label="Language">${links}</nav><h1>${escape(text.title)}</h1>
<p>${escape(text.updatedLabel)}: <time datetime="${androidPrivacy.updated}">${androidPrivacy.updated}</time></p>
<p>${escape(text.operatorLabel)}: ${escape(androidPrivacy.operator)}</p>
${text.sections.map((section) => `<section><h2>${escape(section.title)}</h2><p>${escape(section.text)}</p></section>`).join('\n')}
<footer><h2>${escape(text.contactLabel)}</h2><p><a href="${escape(androidPrivacy.impressum)}">${escape(androidPrivacy.impressum)}</a></p><p><a href="mailto:${escape(androidPrivacy.email)}">${escape(androidPrivacy.email)}</a></p></footer>
</main></body></html>`
}
