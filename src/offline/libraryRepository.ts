export interface ReadingLocation {
  translationCode: string
  bookSlug: string
  chapter: number
  updatedAt: string
}

export interface Bookmark {
  key: string
  translationCode: string
  translationName: string
  bookSlug: string
  bookName: string
  chapter: number
  verse: number
  text: string
  createdAt: string
}

export interface OfflinePackage {
  key: string
  translationCode: string
  translationName: string
  catalogVersion: string
  chapterCount: number
  approximateBytes: number
  downloadedAt: string
}

export interface LibraryRepository {
  getReadingLocation(): Promise<ReadingLocation | undefined>
  saveReadingLocation(location: ReadingLocation): Promise<void>
  listBookmarks(): Promise<Bookmark[]>
  putBookmark(bookmark: Bookmark): Promise<void>
  deleteBookmark(key: string): Promise<void>
  getPackage(translationCode: string): Promise<OfflinePackage | undefined>
  listPackages(): Promise<OfflinePackage[]>
  putPackage(value: OfflinePackage): Promise<void>
  deletePackage(translationCode: string): Promise<void>
}

export function bookmarkKey(
  translationCode: string,
  bookSlug: string,
  chapter: number,
  verse: number,
): string {
  return `${translationCode}:${bookSlug}:${chapter}:${verse}`
}

export function parseBookmarkKey(key: string): Pick<Bookmark, 'translationCode' | 'bookSlug' | 'chapter' | 'verse'> | undefined {
  const parts = /^([^:]+):([^:]+):(\d+):(\d+)$/.exec(key)
  if (!parts) return undefined
  const chapter = Number(parts[3]), verse = Number(parts[4])
  if (!Number.isSafeInteger(chapter) || chapter < 1 || !Number.isSafeInteger(verse) || verse < 1) return undefined
  return { translationCode: parts[1]!, bookSlug: parts[2]!, chapter, verse }
}
