import type { BibleApi } from '@/api/client'
import type { BibleBook, BibleChapter, TranslationSummary } from '@/api/contracts'
import { ApiError } from '@/api/client'
import { chapterKey } from '@/offline/chapterRepository'
import type { ChapterRepository } from '@/offline/chapterRepository'
import type { LibraryRepository, OfflinePackage } from '@/offline/libraryRepository'
import { validChapter } from './chapterValidation'
import { isSourceAnnotations } from '@/api/sourceAnnotations'

export interface PackageProgress {
  current: number
  total: number
  bookName: string
  chapter: number
}

export interface PackageStatus {
  stored?: OfflinePackage
  catalogVersion: string
  updateAvailable: boolean
  totalChapters: number
  refreshing: boolean
  missingChapters: string[]
}

export function createOfflinePackageService(
  api: BibleApi,
  chapters: ChapterRepository,
  library: LibraryRepository,
) {
  const auditStored = async (translationCode:string) => {
    const previous = await library.getPackage(translationCode)
    const missingChapters:string[]=[]
    let stored=previous?{...previous,complete:false}:undefined
    if(previous?.books?.length) {
      validateBooks(previous.books)
      let readable=0;let complete=true
      const unavailable:string[]=[];const missingVerses:string[]=[]
      for(const book of previous.books)for(let number=1;number<=book.chapters_count;number++) {
        const key=chapterKey(translationCode,book.slug,number)
        const chapter=(await chapters.get(key))?.data
        if(!chapter||!validPackageChapter(chapter,translationCode,book.slug,number)){missingChapters.push(key);complete=false;continue}
        if(!chapter.verses.some(verse=>verse.plain_text.trim())){unavailable.push(`${book.name} ${number}`);complete=false;continue}
        readable++
        const missing=chapter.verses.filter(verse=>!verse.plain_text.trim()).map(verse=>verse.osis_ref)
        missingVerses.push(...missing);if(missing.length)complete=false
      }
      stored={...previous,chapterCount:readable,complete:complete&&previous.complete===true&&previous.finished!==false&&!previous.refreshing&&readable===countChapters(previous.books),unavailable,missingVerses}
    }
    return{stored,refreshing:Boolean(previous?.refreshing),missingChapters}
  }
  const inspect = async (translationCode: string): Promise<PackageStatus> => {
    const audit=await auditStored(translationCode)
    let books:BibleBook[]
    try{books=await api.getBooks(translationCode)}
    catch(error){
      if(!(error instanceof ApiError)||!['offline','timeout'].includes(error.kind)||!audit.stored?.books?.length)throw error
      books=audit.stored.books
    }
    validateBooks(books)
    const catalogVersion=createCatalogVersion(books)
    return {
      ...audit,
      catalogVersion,
      updateAvailable: Boolean(audit.stored && audit.stored.catalogVersion !== catalogVersion),
      totalChapters: countChapters(books),
    }
  }

  return {
    inspect,
    auditStored,
    async download(
      translation: TranslationSummary,
      onProgress: (progress: PackageProgress) => void,
      signal?: AbortSignal,
      options?: {forceRefresh?:boolean},
    ): Promise<OfflinePackage> {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const books = await api.getBooks(translation.code)
      validateBooks(books)
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const total = countChapters(books)
      let current = 0
      let approximateBytes = 0
      const unavailable: string[] = []
      const missingVerses: string[] = []
      const previous=await library.getPackage(translation.code)
      const catalogVersion=createCatalogVersion(books)
      const refreshing=Boolean(options?.forceRefresh||previous?.refreshing)
      const oldRefresh=previous?.refresh
      const previousRevision=oldRefresh?.contentRevision??oldRefresh?.translation?.content_revision
      const requestedRevision=translation.content_revision
      const revisionChanged=Boolean(previousRevision&&requestedRevision&&previousRevision!==requestedRevision)
      const resumeRefresh=refreshing&&oldRefresh?.catalogVersion===catalogVersion&&!revisionChanged&&
        (!oldRefresh.translation||oldRefresh.translation.code===translation.code)
      const target=resumeRefresh&&oldRefresh?.translation?oldRefresh.translation:translation
      const targetTranslation={...target,language:{...target.language}}
      const targetRevision=resumeRefresh?(previousRevision??targetTranslation.content_revision??null):(targetTranslation.content_revision??null)
      const startedAt=resumeRefresh?oldRefresh!.startedAt:new Date().toISOString()
      const pending=new Set(resumeRefresh?oldRefresh!.pending:
        books.flatMap(book=>Array.from({length:book.chapters_count},(_,index)=>chapterKey(translation.code,book.slug,index+1))))
      const value: OfflinePackage = {
        key: `package:${translation.code}`, translationCode: translation.code, translationName: targetTranslation.name,
        translation: targetTranslation, books, totalChapters: total, catalogVersion: createCatalogVersion(books), chapterCount: 0,
        approximateBytes: 0, downloadedAt: new Date().toISOString(), finished: false, complete: false, unavailable, missingVerses,
        refreshing,
      }
      const checkpoint = async () => {
        value.chapterCount = current; value.approximateBytes = approximateBytes
        value.refresh=refreshing&&!value.complete?{catalogVersion,translation:targetTranslation,contentRevision:targetRevision,startedAt,books,totalChapters:total,pending:[...pending],unavailable:[...unavailable],missingVerses:[...missingVerses]}:undefined
        // Preserve old readable metadata while recording which old chapter bodies still need refresh.
        const saved=refreshing&&previous&&!value.complete?{...previous,refreshing:true,refresh:value.refresh}:value
        await library.putPackage({ ...saved, unavailable: [...saved.unavailable??[]], missingVerses: [...saved.missingVerses??[]] })
      }
      await checkpoint()

      for (const book of books) {
        for (let chapterNumber = 1; chapterNumber <= book.chapters_count; chapterNumber += 1) {
          if (signal?.aborted) {
            throw new DOMException('Загрузка остановлена.', 'AbortError')
          }
          const key=chapterKey(translation.code,book.slug,chapterNumber)
          const cached=await chapters.get(key)
          let chapter=cached?.data
          const canReuse=chapter&&completeChapter(chapter,translation.code,book.slug,chapterNumber)&&
            (!refreshing||!pending.has(key)&&Boolean(cached?.savedAt&&cached.savedAt>=startedAt))
          if (!canReuse) {
            if(refreshing)pending.add(key)
            await pause(350, signal)
            for (let attempt = 0; ; attempt++) {
              try {
                chapter=await api.getChapter(translation.code,book.slug,chapterNumber)
                if(signal?.aborted)throw new DOMException('Aborted','AbortError')
                if(!validPackageChapter(chapter,translation.code,book.slug,chapterNumber))throw new Error('Invalid chapter in Bible package')
                // Incomplete new source data must not replace a previous readable chapter on refresh.
                if(!refreshing||completeChapter(chapter,translation.code,book.slug,chapterNumber))
                  await chapters.put({key,savedAt:new Date().toISOString(),data:chapter})
                break
              }
              catch (error) {
                if (!(error instanceof ApiError) || ![429, 503, 502, 500, 504].includes(error.status ?? 0) || attempt >= 3) throw error
                await pause(Math.max(1000, error.retryAfterMs ?? 30_000), signal)
              }
            }
          }
          if (!chapter||!validPackageChapter(chapter, translation.code, book.slug, chapterNumber)) throw new Error('Invalid chapter in Bible package')
          if (chapter.verses.every(verse => !verse.plain_text.trim())) {
            unavailable.push(`${book.name} ${chapterNumber}`)
            await checkpoint()
            onProgress({ current, total, bookName: book.name, chapter: chapterNumber })
            continue
          }
          missingVerses.push(...chapter.verses.filter(verse => !verse.plain_text.trim()).map(verse => verse.osis_ref))
          if(completeChapter(chapter,translation.code,book.slug,chapterNumber))pending.delete(key)
          approximateBytes += new Blob([JSON.stringify(chapter)]).size
          current += 1
          await checkpoint()
          onProgress({ current, total, bookName: book.name, chapter: chapterNumber })
        }
      }

      if(signal?.aborted)throw new DOMException('Aborted','AbortError')
      value.finished = !refreshing||pending.size===0
      value.complete = current === total && missingVerses.length === 0 && (!refreshing||pending.size===0)
      if(value.complete){value.refreshing=false;value.refresh=undefined}
      await checkpoint()
      return value
    },
  }
}

function validateBooks(books:BibleBook[]):void {
  if (!books.length || books.some(book => !book.slug || !Number.isSafeInteger(book.chapters_count) || book.chapters_count <= 0)
      || new Set(books.map(book => book.slug)).size !== books.length) throw new Error('Invalid Bible book catalog')
}
function validPackageChapter(chapter:BibleChapter,code:string,slug:string,number:number):boolean {
  try {return validChapter(chapter,code,slug,number)&&chapter.verses.every(verse=>!verse.annotations||isSourceAnnotations(verse.annotations,verse.plain_text))}
  catch{return false}
}
function completeChapter(chapter:BibleChapter,code:string,slug:string,number:number):boolean {
  return validPackageChapter(chapter,code,slug,number)&&chapter.verses.length>0&&chapter.verses.every(verse=>verse.plain_text.trim())
}

function pause(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve() }, ms)
    if (signal?.aborted) abort()
    else signal?.addEventListener('abort', abort, { once: true })
  })
}

export function createCatalogVersion(books: BibleBook[]): string {
  return `catalog-v1:${books.map((book) => `${book.slug}:${book.chapters_count}`).join('|')}`
}

function countChapters(books: BibleBook[]): number {
  return books.reduce((total, book) => total + book.chapters_count, 0)
}
