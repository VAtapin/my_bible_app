import type { Stemmer } from 'snowball-stemmers'
const languages: Record<string,string>={ru:'russian',rus:'russian',de:'german',deu:'german',ger:'german',en:'english',eng:'english'}
const prepared=new Map<string,Stemmer>()
let preparation:Promise<void>|undefined
export function prepareSearchStemming() {
  return preparation??=import('snowball-stemmers').then(factory=>{for(const algorithm of new Set(Object.values(languages)))prepared.set(algorithm,factory.newStemmer(algorithm))})
}
export function searchStem(value:string,language:string):string|undefined {
  const stemmer=prepared.get(languages[language]??'')
  return stemmer?.stem(value.normalize('NFC').toLocaleLowerCase().replaceAll('ё','е'))
}
export function supportsSearchStemming(language:string) {return language in languages}
