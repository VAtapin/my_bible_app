import { createRequire } from 'node:module'
import { createServer } from 'node:http'
import { readFileSync, mkdirSync } from 'node:fs'
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
const texts = {
  ru: ['Для чтения и молитвы', 'Библия · Молитвы · Церковный календарь', 'Сравнивайте переводы. Читайте без сети.'],
  de: ['Lesen und beten', 'Bibel · Gebete · Kirchenkalender', 'Übersetzungen vergleichen. Offline lesen.'],
  uk: ['Для читання й молитви', 'Біблія · Молитви · Церковний календар', 'Порівнюйте переклади. Читайте без мережі.'],
  en: ['For reading and prayer', 'Bible · Prayers · Church calendar', 'Compare translations. Read offline.'],
}
const server = createServer((request, response) => {
  const resources = {
    '/church.png': ['public/brand/welcome-church.png', 'image/png'],
    '/serif.ttf': ['mobile/androidApp/src/main/res/font/noto_serif.ttf', 'font/ttf'],
    '/inter.ttf': ['mobile/androidApp/src/main/res/font/inter.ttf', 'font/ttf'],
  }
  const resource = resources[request.url]
  if (resource) { response.setHeader('Content-Type', resource[1]); response.end(readFileSync(join(root, resource[0]))); return }
  const language = request.url.slice(1)
  const text = texts[language]
  if (!text && language !== 'icon') { response.writeHead(404); response.end(); return }
  response.setHeader('Content-Type', 'text/html; charset=utf-8')
  response.end(language === 'icon'
    ? '<!doctype html><style>body{margin:0;background:#f7f5f0}img{display:block;width:512px;height:512px;object-fit:cover;object-position:50% 40%}</style><img src="/church.png">'
    : `<!doctype html><html lang="${language}"><style>
@font-face{font-family:Serif;src:url('/serif.ttf')}@font-face{font-family:Inter;src:url('/inter.ttf')}
*{box-sizing:border-box}body{margin:0;width:1024px;height:500px;background:#f8f6ef;color:#17354b;font-family:Inter}
.photo{position:absolute;inset:0 auto 0 0;width:370px;height:500px;object-fit:cover;object-position:center 30%}
.fade{position:absolute;inset:0 auto 0 300px;width:80px;background:linear-gradient(90deg,transparent,#f8f6ef)}
main{position:absolute;left:412px;top:64px;right:38px}.eyebrow{font-size:18px;color:#967637;letter-spacing:1px}
h1{font:700 44px/1.2 Serif;margin:23px 0 18px}h2{font:500 28px/1.3 Serif;margin:0 0 26px}
p{font-size:18px;line-height:1.7;margin:8px 0}.line{width:54px;height:2px;background:#b49142;margin:25px 0}
</style><img class="photo" src="/church.png"><div class="fade"></div><main><div class="eyebrow">${text[0]}</div><h1>Bible Desktop</h1><h2>${text[1]}</h2><div class="line"></div><p>${text[2]}</p></main></html>`)
})
await new Promise((accept) => server.listen(0, '127.0.0.1', accept))
const browser = await chromium.launch({ channel: process.env.STORE_BROWSER_CHANNEL || 'msedge', headless: true })
try {
  for (const language of [...Object.keys(texts), 'icon']) {
    const size = language === 'icon' ? { width: 512, height: 512 } : { width: 1024, height: 500 }
    const page = await browser.newPage({ viewport: size, deviceScaleFactor: 1 })
    await page.goto(`http://127.0.0.1:${server.address().port}/${language}`)
    await page.evaluate(() => document.fonts.ready)
    await page.locator('img').evaluate((img) => img.decode())
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth || document.documentElement.scrollHeight > innerHeight)) throw new Error(`Layout overflow: ${language}`)
    await page.screenshot({ path: join(out, language === 'icon' ? 'icon-512.png' : `feature-${language}.png`) })
    await page.close()
    console.log(`PASS: ${language} ${size.width}x${size.height}, opaque PNG`)
  }
} finally { await browser.close(); server.close() }
