import{describe,it,expect}from'vitest'
import{measuredPageDistance,firstReadingLineDistance}from'./readerPaging'
describe('measured reader page alignment',()=>{
 it('puts the actual clipped line first, regardless of font size or paragraph gaps',()=>{
  expect(measuredPageDistance([{top:12,bottom:37},{top:48,bottom:89},{top:114,bottom:147}],10,130,1)).toBe(104)
 })
 it('starts at the next actual line when the viewport ends between lines',()=>{
  expect(measuredPageDistance([{top:12,bottom:37},{top:48,bottom:89},{top:114,bottom:147}],10,100,1)).toBe(104)
 })
 it('uses measured previous lines and aligns after a lazy-layout fallback',()=>{
  expect(measuredPageDistance([{top:-109,bottom:-78},{top:-60,bottom:-29},{top:9,bottom:40}],10,110,-1)).toBe(-119)
  expect(measuredPageDistance([{top:10,bottom:40}],10,110,-1)).toBeUndefined()
  expect(firstReadingLineDistance([{top:1,bottom:27},{top:42,bottom:81}],10,110)).toBe(-9)
 })
})
