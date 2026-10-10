import {describe,it,expect}from'vitest'
import {parseSourceMarkup}from'./sourceMarkup'
describe('declared source markup',()=>{
 it('never labels ordinary HTML italic as supplied words',()=>{expect(parseSourceMarkup('Earth <i>was</i> empty',undefined).addedWords).toEqual([])})
 it('reads MyBible inserted words and embedded headings using actual offsets',()=>{const value=parseSourceMarkup('<pb/>Earth <i>was</i> empty<h>Others</h> Now','mybible');expect(value.text).toBe('Earth was empty Now');expect(value.addedWords).toEqual([{start:6,end:9}]);expect(value.headings).toEqual([{text:'Others',offset:15}])})
 it('keeps markers unresolved until an associated source supplies the note',()=>{const value=parseSourceMarkup('God<f>[1]</f> speaks<n>Source explanation</n><S>430</S>.','mybible');expect(value.text).toBe('God speaks.');expect(value.footnotes).toEqual([{marker:'[1]',text:null,offset:3},{marker:'*',text:'Source explanation',offset:10}]);expect(value.text).not.toContain('430')})
 it('drops executable source fragments and preserves paragraph anchors',()=>{const value=parseSourceMarkup('First<script>alert(1)</script><pb/>Next &amp; last','mybible');expect(value.text).toBe('First Next & last');expect(value.paragraphs).toEqual([6]);expect(value.headings).toEqual([])})
})
