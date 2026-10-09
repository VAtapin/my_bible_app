import { createRequire } from 'node:module'
import { createServer } from 'node:http'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// Code-native store layout, using the unchanged project photograph and bundled fonts.
// Supply PLAYWRIGHT_MODULE_DIRECTORY when Playwright is provided by the local tooling.
const require = createRequire(process.env.PLAYWRIGHT_MODULE_DIRECTORY
  ? join(process.env.PLAYWRIGHT_MODULE_DIRECTORY, 'package.json') : import.meta.url)
const { chromium } = require('playwright')
const root = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const out = join(root, 'mobile/play/assets')
mkdirSync(out, { recursive: true })
const texts = JSON.parse(readFileSync(new URL('./banner-copy.json', import.meta.url), 'utf8'))
const escape = (value) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const server = createServer((request, response) => {
  const resources = {
    '/church.png': ['public/brand/welcome-church.png', 'image/png'],
    '/serif.ttf': ['mobile/androidApp/src/main/res/font/noto_serif.ttf', 'font/ttf'],
    '/inter.ttf': ['mobile/androidApp/src/main/res/font/inter.ttf', 'font/ttf'],
  }
  const resource = resources[request.url]
  if (resource) { response.setHeader('Content-Type', resource[1]); response.end(readFileSync(join(root, resource[0]))); return }
  const url = new URL(request.url, 'http://localhost')
  const language = url.pathname.slice(1)
  const web = url.searchParams.get('format') === 'web'
  const text = texts[language]
  if (!text && language !== 'icon') { response.writeHead(404); response.end(); return }
  response.setHeader('Content-Type', 'text/html; charset=utf-8')
  response.end(language === 'icon'
    ? '<!doctype html><style>body{margin:0;background:#f7f5f0}img{display:block;width:512px;height:512px;object-fit:cover;object-position:50% 40%}</style><img src="/church.png">'
    : `<!doctype html><html lang="${language}"><style>
@font-face{font-family:Serif;src:url('/serif.ttf')}@font-face{font-family:Inter;src:url('/inter.ttf')}
*{box-sizing:border-box}body{margin:0;width:${web ? 1200 : 1024}px;height:${web ? 630 : 500}px;background:#f8f6ef;color:#17354b;font-family:Inter}
.photo{position:absolute;inset:0 auto 0 0;width:${web ? 342 : 280}px;height:100%;object-fit:cover;object-position:center 30%}
.fade{position:absolute;inset:0 auto 0 ${web ? 282 : 225}px;width:65px;background:linear-gradient(90deg,transparent,#f8f6ef)}
main{position:absolute;left:${web ? 375 : 310}px;top:${web ? 38 : 24}px;right:28px}
h1{font:700 ${web ? 50 : 42}px/1.15 Serif;margin:0 0 8px}.headline{font:500 ${web ? 24 : 22}px/1.3 Serif;color:#916d2b;margin:0 0 10px}
.intro{font-size:${web ? 20 : 17}px;line-height:1.45;margin:0 0 ${web ? 22 : 16}px}
.features{display:grid;grid-template-columns:1fr 1fr;gap:12px}.card{background:white;border:1px solid #d6e1e8;border-radius:14px;padding:${web ? 18 : 13}px}
h2{font-size:${web ? 22 : 18}px;line-height:1.35;margin:0 0 7px}.detail{font-size:${web ? 18 : 15}px;line-height:1.45;margin:0}
.footer{font-size:${web ? 19 : 17}px;color:#315b7a;line-height:1.4;margin:16px 0 5px}.devices{font-size:${web ? 17 : 14}px;line-height:1.4;margin:0}
</style><img class="photo" src="/church.png"><div class="fade"></div><main><h1>Bible App</h1><p class="headline">${escape(text.headline)}</p><p class="intro">${escape(text.intro)}</p><div class="features">${text.features.map(([title, detail]) => `<section class="card"><h2>${escape(title)}</h2><p class="detail">${escape(detail)}</p></section>`).join('')}</div><p class="footer">${escape(text.footer)}</p><p class="devices">${escape(text.devices)}</p></main></html>`)
})
await new Promise((accept) => server.listen(0, '127.0.0.1', accept))
let browser
try {
  browser = await chromium.launch({ channel: process.env.STORE_BROWSER_CHANNEL || 'msedge', headless: true })
  for (const [language, web] of [...Object.keys(texts).flatMap((language) => [[language, false], [language, true]]), ['icon', false]]) {
    const size = language === 'icon' ? { width: 512, height: 512 } : web ? { width: 1200, height: 630 } : { width: 1024, height: 500 }
    const page = await browser.newPage({ viewport: size, deviceScaleFactor: 1 })
    await page.goto(`http://127.0.0.1:${server.address().port}/${language}${web ? '?format=web' : ''}`)
    await page.evaluate(() => document.fonts.ready)
    await page.locator('img').evaluate((img) => img.decode())
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth || document.documentElement.scrollHeight > innerHeight)) throw new Error(`Layout overflow: ${language}`)
    const target = web ? join(root, `public/brand/share-${language}.jpg`) : join(out, language === 'icon' ? 'icon-512.png' : `feature-${language}.png`)
    const pixels = await page.screenshot(web ? { type: 'jpeg', quality: 92 } : { type: 'png' })
    for (let attempt = 0; ; attempt++) {
      try { writeFileSync(target, pixels); break }
      catch (error) {
        if (!['UNKNOWN', 'EBUSY'].includes(error.code) || attempt >= 4) throw error
        await new Promise((accept) => setTimeout(accept, 200 * (attempt + 1)))
      }
    }
    await page.close()
    console.log(`PASS: ${language} ${size.width}x${size.height}, ${web ? 'web share JPEG' : 'opaque PNG'}`)
  }
} finally { await browser?.close(); server.close() }
