import { ApiError } from '@/api/client'
import type { DictionaryApi } from '@/api/dictionaries'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import type { StudyCache } from './studyService'
import { installedDictionaryModules,installedDictionaryEntries,installedDictionaryArticle,installedDictionaryLookup,installedDictionaryContext } from './installedDictionaries'
export function createDictionaryService(api: DictionaryApi, cache: StudyCache = { read: readCalendarState, write: writeCalendarState }) {
  async function saved<T>(key: string, fetch: () => Promise<T>) { try { const value = await fetch(); await cache.write(key, value).catch(() => undefined); return value } catch (error) { if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error; const value = await cache.read<T>(key); if (value === undefined) throw error; return value } }
  return {
    modules: async () => {const local=await installedDictionaryModules();try{const remote=await saved('dictionary:modules',api.modules);return [...new Map([...remote,...local].map(m=>[m.code,m])).values()]}catch(error){if(local.length && error instanceof ApiError && ['offline','timeout'].includes(error.kind))return local;throw error}},
    entries: async(code:string,q='',offset=0)=>await installedDictionaryEntries(code,q,offset)??saved(`dictionary:entries:${code}:${q}:${offset}`,()=>api.entries(code,q,offset)),
    article: async(code:string,key:string,version:string|null=null)=>await installedDictionaryArticle(code,key)??saved(`dictionary:article:${code}:${version}:${key}`,()=>api.article(code,key)),
    lookup: async(q:string,codes:string[]=[])=>{const local=await installedDictionaryLookup(q,codes);if(local.allInstalled)return local.data;const remote=await saved(`dictionary:lookup:${[...codes].sort().join(',')}:${q}`,()=>api.lookup(q,codes));return[...new Map([...remote,...local.data].map(row=>[`${row.module_code}:${row.standard_form}`,row])).values()]},
    context: async(book:string,chapter:number|null,codes:string[],offset=0)=>await installedDictionaryContext(book,chapter,codes,offset)??saved(`dictionary:context:${book}:${chapter}:${codes.join(',')}:${offset}`,()=>api.context(book,chapter,codes,offset)),
    verse:(verse:number,codes:string[],offset=0)=>saved(`dictionary:verse:${verse}:${codes.join(',')}:${offset}`,()=>api.verse(verse,codes,offset)),
    verseAt:async(verse:number,book:string,chapter:number,number:number,codes:string[],offset=0,osis?:string)=>await installedDictionaryContext(book,chapter,codes,offset,number,number,osis)??saved(`dictionary:verse:${verse}:${codes.join(',')}:${offset}`,()=>api.verse(verse,codes,offset)),
  }
}
