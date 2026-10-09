// Public BibleDesktop GET responses only. Generated application content, not user data.
// Run explicitly when refreshing the bundled edition; normal builds never require network.
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { gzipSync } from 'node:zlib'
import { createHash } from 'node:crypto'
const code = 'BQ_RUSSIAN_RST_STRONG'
const base = 'https://bible-desktop.com/api'
const cache = new URL('../androidApp/build/bundled-bible-source/', import.meta.url)
// Do not use .gz: Android's asset packager transparently expands/renames that extension.
const output = new URL('../androidApp/src/main/assets/bibles/synodal.bundle', import.meta.url)
await mkdir(cache, { recursive: true })
async function get(path) {
  for (let attempt = 0; ; attempt++) {
    const response = await fetch(`${base}${path}`, { signal: AbortSignal.timeout(60_000) })
    if ((response.status === 429 || response.status >= 500) && attempt < 5) {
      const delay = Number(response.headers.get('Retry-After')) || 30
      await new Promise(resolve => setTimeout(resolve, delay * 1000)); continue
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${path}`)
    const body = await response.json()
    if (!body.data) throw new Error(`Invalid envelope: ${path}`)
    return body.data
  }
}
const catalog = await get('/translations?catalog=available&language=ru')
const translation = catalog.find(item => item.code === code)
if (!translation) throw new Error('Default Synodal edition not found')
const { books } = await get(`/translations/${code}/books`)
if (!books.length || books.length > 200) throw new Error('Invalid books')
const records = [], unavailable = [], missingVerses = []
let current = 0, bytes = 0
for (const book of books) for (let number = 1; number <= book.chapters_count; number++) {
  const path = `/translations/${code}/books/${encodeURIComponent(book.slug)}/chapters/${number}`
  const cached = new URL(`${createHash('sha256').update(path).digest('hex')}.json`, cache)
  let chapter
  try { chapter = JSON.parse(await readFile(cached, 'utf8')) }
  catch {
    chapter = await get(path)
    await writeFile(cached, JSON.stringify(chapter))
    await new Promise(resolve => setTimeout(resolve, 350))
  }
  if (chapter.translation.code !== code || chapter.book.slug !== book.slug || chapter.chapter.number !== number
    || chapter.verses.length !== chapter.chapter.verses_count) throw new Error(`Invalid chapter: ${path}`)
  if (!chapter.verses.some(verse => verse.plain_text.trim())) unavailable.push(`${book.name} ${number}`)
  else {
    if (new Set(chapter.verses.map(verse => verse.osis_ref)).size !== chapter.verses.length) throw new Error(`Duplicate verses: ${path}`)
    const record = JSON.stringify(chapter)
    records.push(record); bytes += Buffer.byteLength(record)
    missingVerses.push(...chapter.verses.filter(verse => !verse.plain_text.trim()).map(verse => verse.osis_ref))
  }
  if (++current % 100 === 0) console.log(`Synodal: ${current} chapters checked`)
}
const pack = { translation, books, done: records.length, total: current, bytes,
  complete: !unavailable.length && !missingVerses.length, unavailable, missingVerses }
if (records.length < 1100) throw new Error('Unexpectedly incomplete Synodal source')
await mkdir(new URL('.', output), { recursive: true })
const compressed = gzipSync([JSON.stringify(pack), ...records, ''].join('\n'), { level: 9, mtime: 0 })
await writeFile(output, compressed)
console.log(JSON.stringify({ chapters: records.length, total: current, unavailable, missingVerses: missingVerses.length, compressedBytes: compressed.length,
  sha256: createHash('sha256').update(compressed).digest('hex') }))
