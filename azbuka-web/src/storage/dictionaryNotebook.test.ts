import 'fake-indexeddb/auto'
import {expect,it} from 'vitest'
import {defaultProfile,loadProfile,saveProfile} from './profileRepository'
import {loadDictionaryNotebook,rememberDictionaryWord,toggleDictionaryFavorite} from './dictionaryNotebook'

it('preserves learning progress and independent favorites while bounding and deduplicating recent articles',async()=>{
 const profile={...defaultProfile(),learnedLetterIds:['az'],answered:17}
 await saveProfile(profile)
 const word={key:'a'.repeat(40),word:'А҆́зъ',civil:'Азъ'}
 await Promise.all([toggleDictionaryFavorite(word),toggleDictionaryFavorite({...word,key:'b'.repeat(40)})])
 expect((await loadDictionaryNotebook()).favorites).toHaveLength(2)
 for(let index=0;index<35;index++)await rememberDictionaryWord({...word,key:index.toString(16).padStart(40,'0')})
 await rememberDictionaryWord(word);await rememberDictionaryWord(word)
 expect((await loadDictionaryNotebook()).recent).toHaveLength(30)
 expect((await loadDictionaryNotebook()).recent[0]).toEqual(word)
 await toggleDictionaryFavorite(word)
 expect((await loadDictionaryNotebook()).favorites.map(item=>item.key)).toEqual(['b'.repeat(40)])
 expect(await loadProfile()).toEqual(profile)
})
