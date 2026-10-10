import type { BibleVerse } from '@/api/contracts'
import type { StudyStrongToken } from '@/api/verseStudy'
import { inlineSourceStrongTokens } from './sourceVerseDisplay'

export function sourceStudyStrongTokens(verse:BibleVerse):StudyStrongToken[]{
 const tokens:StudyStrongToken[]=inlineSourceStrongTokens(verse).map(token=>({strong_number:token.strong_number,token_order:token.token_order,grammar_code:token.grammar_code,surface_text:token.surface_text}))
 const positioned=new Set(tokens.map(token=>token.strong_number))
 return [...tokens,...sourceStrongNumbers(verse.text,verse.has_strong_markup).filter(number=>!positioned.has(number)).map(strong_number=>({strong_number}))]
}
/** Explicit source identifiers only. Bare numbers and token_order never imply a word relation. */
export function sourceStrongNumbers(raw:string,hasMarkup:boolean):string[]{
 if(!hasMarkup)return[]
 const safe=raw.replace(/<(script|style|iframe|object)\b[^>]*>[\s\S]*?<\/\1\s*>/gi,'').replace(/<[^>]*>/g,' ')
 return [...new Set([...safe.matchAll(/(?<![\p{L}\p{N}])([HG])(\d{1,5})(?![\p{L}\p{N}])/giu)].map(m=>`${m[1]!.toUpperCase()}${Number(m[2])}`).filter(n=>Number(n.slice(1))>0))]
}
