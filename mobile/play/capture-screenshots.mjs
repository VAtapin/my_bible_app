import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Run only on a fresh, dedicated release AVD: never clear an existing user's profile.
const serial = process.argv[2]
if (!/^emulator-\d+$/.test(serial || '')) throw new Error('Pass the dedicated emulator serial')
const adbPath = process.env.ANDROID_HOME ? resolve(process.env.ANDROID_HOME, 'platform-tools/adb.exe') : 'adb'
const adb = (...args) => execFileSync(adbPath, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30_000 })
const avd = adb('emu', 'avd', 'name').trim().split('\n')[0].trim()
if (!/^BibleDesktop_Release_(16KB_)?37$/.test(avd)) throw new Error('Use a separate BibleDesktop_Release AVD, not a personal device or prototype')
const destination = resolve(fileURLToPath(new URL('./assets/screenshots/ru/', import.meta.url)))
mkdirSync(destination, { recursive: true })
const wait = (ms) => new Promise((accept) => setTimeout(accept, ms))
async function ui() {
  adb('shell', 'uiautomator', 'dump', '/sdcard/bible-store-window.xml')
  return adb('shell', 'cat', '/sdcard/bible-store-window.xml')
}
async function click(label, attribute = 'text') {
  const xml = await ui()
  const node = [...xml.matchAll(/<node\s+[^>]*>/g)].map((match) => match[0])
    .find((tag) => tag.includes(`${attribute}="${label}"`))
  if (!node) throw new Error(`Control not visible: ${label}`)
  const bounds = node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).slice(1).map(Number)
  adb('shell', 'input', 'tap', String(Math.round((bounds[0] + bounds[2]) / 2)), String(Math.round((bounds[1] + bounds[3]) / 2)))
  await wait(500)
}
async function expect(label) {
  for (let i = 0; i < 15; i++) {
    if ((await ui()).includes(label)) return
    await wait(1000)
  }
  throw new Error(`Screen did not become ready: ${label}`)
}
async function screenshot(name) {
  await wait(800)
  const bytes = execFileSync(adbPath, ['-s', serial, 'exec-out', 'screencap', '-p'], { timeout: 30_000, maxBuffer: 16 * 1024 * 1024 })
  writeFileSync(resolve(destination, `${name}.png`), bytes)
  console.log(`Captured actual signed APK: ${name}`)
}
adb('shell', 'wm', 'size', '1080x1920')
adb('shell', 'wm', 'density', '420')
adb('shell', 'settings', 'put', 'system', 'show_touches', '0')
adb('shell', 'am', 'start', '-n', 'com.bibledesktop.myapp/.MainActivity')
if (!process.argv.includes('--resume-reader')) {
  await expect('Русский')
  if (!(await ui()).includes('Quick setup') && !(await ui()).includes('Быстро настроить')) throw new Error('Fresh unconfigured release required; do not reset user data')
  await click('Русский')
  await expect('Быстро настроить')
  await screenshot('01-welcome')
  await click('Быстро настроить')
  await expect('Мой день')
  await expect('Старый стиль')
  await screenshot('02-home')
  await click('Календарь')
  await expect('Церковный календарь')
  await screenshot('03-calendar')
  await click('На главную', 'content-desc')
}
// Real read-only BibleDesktop API, not a simulated store screen.
const response = await fetch('https://bible-desktop.com/api/translations?language=ru')
if (!response.ok) throw new Error(`Translations HTTP ${response.status}`)
const translations = (await response.json()).data
const edition = translations.find((item) => item.code === 'RST-Strong') || translations.find((item) => item.language?.code === 'ru')
if (!edition) throw new Error('Russian translation missing')
const booksResponse = await fetch(`https://bible-desktop.com/api/translations/${encodeURIComponent(edition.code)}/books`)
if (!booksResponse.ok) throw new Error(`Books HTTP ${booksResponse.status}`)
const book = (await booksResponse.json()).data.books.find((item) => item.canonical_book?.osis_code === 'John')
if (!book) throw new Error('Canonical John book missing')
const url = `https://bible-app.online/reader?translation=${encodeURIComponent(edition.code)}&book=${encodeURIComponent(book.slug)}&chapter=3&verse=16`
adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', `'${url}'`, '-p', 'com.bibledesktop.myapp')
await expect('Сравнить переводы')
await expect('Бог')
await screenshot('04-reader')
await click('Сравнить переводы')
await expect('Один перевод')
await wait(2500)
await screenshot('05-comparison')
console.log('No profiles were uploaded or cleared. Screenshot locale: ru; other locale screenshots still need capture.')
