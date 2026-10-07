import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { createVerseNoteRepository } from './verseNotes'
import { createIndexedDbLibraryRepository } from './indexedDbLibraryRepository'
describe('local verse notes', () => {
  beforeEach(() => { globalThis.indexedDB = new IDBFactory() })
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
  it('lists pre-existing notes without metadata and excludes other local state', async () => {
    const notes = createVerseNoteRepository(), library = createIndexedDbLibraryRepository()
    await library.saveReadingLocation({ translationCode: 'RST', bookSlug: 'john', chapter: 1, updatedAt: '' })
    await library.putPackage({ key: 'RST', translationCode: 'RST', translationName: 'Russian', catalogVersion: '1', chapterCount: 1, approximateBytes: 1, downloadedAt: '' })
    const older = { key: 'RST:john:1:1', text: 'Existing note\nSecond line', updatedAt: '2026-10-07T10:00:00Z' }
    const newer = { key: 'DE:john:1:2', text: 'Other translation', updatedAt: '2026-10-07T12:00:00Z' }
    await notes.save(older); await notes.save(newer)
    expect(await createVerseNoteRepository().list()).toEqual([newer, older])
    await notes.save({ ...older, text: 'Edited note', updatedAt: '2026-10-07T13:00:00Z' })
    expect((await notes.list()).map((note) => note.key)).toEqual([older.key, newer.key])
    expect((await notes.list())[0]?.text).toBe('Edited note')
    expect((await library.getReadingLocation())?.bookSlug).toBe('john')
  })
  it('preserves saved verse metadata when a note is edited', async () => {
    const repository = createVerseNoteRepository()
    const note = { key: 'RST:john:1:1', text: 'First', updatedAt: '', location: { translationCode: 'RST', translationName: 'Russian', bookSlug: 'john', bookName: 'John', chapter: 1, verse: 1 }, verseText: 'In the beginning' }
    await repository.save(note)
    const saved = (await repository.list())[0]!
    await repository.save({ ...saved, text: 'Edited', updatedAt: '2026-10-07' })
    expect((await repository.get(note.key))?.location).toEqual(note.location)
    expect((await repository.get(note.key))?.verseText).toBe(note.verseText)
  })
})
