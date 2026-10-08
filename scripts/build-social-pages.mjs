import { mkdir, readFile, writeFile, stat } from 'node:fs/promises'
import { localizedSocialPage, socialPages } from './social-metadata.mjs'
const dist = new URL('../dist/', import.meta.url)
const template = await readFile(new URL('index.html', dist), 'utf8')
for (const language of Object.keys(socialPages)) {
  // Failing here is preferable to publishing a broken messenger preview.
  if (!(await stat(new URL(`brand/share-${language}.jpg`, dist))).size) throw new Error('Missing social image')
  await mkdir(new URL(`${language}/`, dist), { recursive: true })
  await writeFile(new URL(`${language}/index.html`, dist), localizedSocialPage(template, language))
}
await writeFile(new URL('index.html', dist), localizedSocialPage(template, 'ru', true))
console.log('Built RU/DE/UK/EN share pages with static Open Graph metadata.')
