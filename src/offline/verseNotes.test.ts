import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { createVerseNoteRepository } from './verseNotes'
import { createIndexedDbLibraryRepository } from './indexedDbLibraryRepository'
describe('local verse notes', () => {
  it('persists edits across instances and isolates verses, translations and reading state', async () => {
    const first = createVerseNoteRepository()
    const library = createIndexedDbLibraryRepository()
    await library.saveReadingLocation({ translationCode: 'RST', bookSlug: 'john', chapter: 1, updatedAt: '' })
    await first.save({ key: 'RST:john:1:1', text: 'My note', updatedAt: '2026-10-07' })
    const reopened = createVerseNoteRepository()
    expect((await reopened.get('RST:john:1:1'))?.text).toBe('My note')
    expect(await reopened.get('DE:john:1:1')).toBeUndefined()
    expect(await reopened.get('RST:john:1:2')).toBeUndefined()
    await reopened.save({ key: 'RST:john:1:1', text: 'Edited', updatedAt: '2026-10-08' })
    expect((await first.get('RST:john:1:1'))?.text).toBe('Edited')
    expect((await library.getReadingLocation())?.chapter).toBe(1)
  })
})
