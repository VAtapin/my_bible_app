import {describe,expect,it} from 'vitest'
import {sourceStrongNumbers} from './strongMarkup'
describe('explicit source Strong markup',()=>{
 it('preserves Hebrew/Greek identity and deduplicates published identifiers',()=>{expect(sourceStrongNumbers('Бог H0430 <S>G25</S> H430',true)).toEqual(['H430','G25'])})
 it('does not guess bare numbers, substring matches or dictionaries for unmarked editions',()=>{expect(sourceStrongNumbers('430 Бог G250000 XH430 H0',true)).toEqual([]);expect(sourceStrongNumbers('H430',false)).toEqual([])})
 it('ignores executable content and HTML attributes as identifiers',()=>{expect(sourceStrongNumbers('<script>H1</script><span title="H2">Бог</span> <S>H430</S>',true)).toEqual(['H430'])})
})
