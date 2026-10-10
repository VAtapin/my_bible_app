import type {DictionaryArticle,DictionaryModule} from '@/api/dictionaries'
import {prayerExcerpt} from './prayerContent'
export const dictionaryIsAtlas=(module:DictionaryModule)=>module.kind==='atlas'
export function dictionaryArticleLinks(links:DictionaryArticle['links']){
 return links.map(link=>({...link,label:prayerExcerpt(link.label)||prayerExcerpt(link.topic)})).filter(link=>link.label)
}
