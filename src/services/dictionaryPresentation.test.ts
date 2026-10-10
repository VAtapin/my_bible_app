import {expect,it} from 'vitest'
import {dictionaryArticleLinks,dictionaryIsAtlas} from './dictionaryPresentation'
it('uses the actual related article title when the label consists only of imported whitespace',()=>{
 expect(dictionaryArticleLinks([{key:'a',label:'\u2003',topic:'Исход из Египта'},{key:'b',label:'<p>&nbsp;</p>',topic:'\u2003'}])).toEqual([{key:'a',label:'Исход из Египта',topic:'Исход из Египта'}])
})
it('uses published source kind for the atlas catalogue',()=>{
 expect(dictionaryIsAtlas({kind:'atlas'} as never)).toBe(true)
 expect(dictionaryIsAtlas({kind:'articles'} as never)).toBe(false)
})
