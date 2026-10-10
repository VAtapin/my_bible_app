import { describe, it, expect } from 'vitest'
import { ReaderNavigationHistory } from './readerHistory'
import type { KeyValueStorage } from '@/profile/profileRepository'
const storage = (): KeyValueStorage => { const data=new Map<string,string>();return {getItem:key=>data.get(key)??null,setItem:(key,value)=>{data.set(key,value)},removeItem:key=>{data.delete(key)}} }
const place = (book:string,chapter=1,verse=1,offset=0,code='RST') => ({code,book,chapter,verse,offset})
describe('navigation history',()=>{
  it('returns from later reading to the destination then to the departure and forward',()=>{
    const h=new ReaderNavigationHistory(storage(),'window-0')
    h.observe(place('Exod',4,5,11));h.navigate(place('Ps',22,3));h.observe(place('Ps',23,1,17))
    expect(h.state.entries).toHaveLength(2)
    expect(h.back()).toEqual(place('Ps',22,3));expect(h.back()).toEqual(place('Exod',4,5,11))
    expect(h.forward()).toEqual(place('Ps',22,3));expect(h.forward()).toEqual(place('Ps',23,1,17))
  })
  it('captures departure after scrolling, removes the abandoned forward branch and restores each window',()=>{
    const s=storage(),a=new ReaderNavigationHistory(s,'a'),b=new ReaderNavigationHistory(s,'b')
    a.observe(place('Gen'));a.observe(place('Gen',2,4,25));a.navigate(place('Matt',4,3,0,'KJV'))
    b.observe(place('Luke',1,1,9));a.back();a.navigate(place('Acts',2,8))
    expect(a.canForward).toBe(false);expect(new ReaderNavigationHistory(s,'a').current).toEqual(place('Acts',2,8))
    expect(new ReaderNavigationHistory(s,'b').current).toEqual(place('Luke',1,1,9))
  })
  it('ignores corrupt data and bounds saved history',()=>{
    const s=storage();s.setItem('x','{"entries":[{}],"cursor":0}');const h=new ReaderNavigationHistory(s,'x')
    expect(h.canBack).toBe(false);for(let ch=1;ch<250;ch++)h.navigate(place('Ps',ch));expect(h.state.entries).toHaveLength(200)
    expect(new ReaderNavigationHistory(s,'x').current?.chapter).toBe(249)
    h.select(0);h.observe(place('Ps',51,3,20));expect(h.back()).toEqual(place('Ps',50))
    expect(h.state.entries).toHaveLength(200);expect(new ReaderNavigationHistory(s,'x').state.cursor).toBe(0)
  })
})
