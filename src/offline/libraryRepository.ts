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
