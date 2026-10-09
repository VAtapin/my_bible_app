import{describe,it,expect}from'vitest'
import{readWindows,windowRatio,mergeWindowLocation,type ReaderWindows}from'./readerWindows'
describe('reader window restoration',()=>{
  const place={code:'RST',book:'acts',chapter:6,verse:2,offset:12}
  const state={places:[place,{...place,book:'john',chapter:3,verse:16}],active:1,sync:false,ratio:.65,open:[true,false]}
  it('preserves independent passages, active window and the closed window',()=>expect(readWindows(JSON.stringify(state))).toEqual(state))
  it.each([{...state,places:[place]},{...state,places:[place,{...place,chapter:0}]},{...state,open:[false,false]},{...state,active:2}])('rejects malformed configuration without guessing places',value=>expect(readWindows(JSON.stringify(value))).toBeUndefined())
  it('keeps both panes usable when a saved ratio exceeds the bounds',()=>{expect(windowRatio(.99)).toBe(.8);expect(windowRatio(-2)).toBe(.2)})
  it('keeps the active second pane when synchronized places coincide',()=>{
    const saved={...state,places:[place,place],open:[true,true]} as ReaderWindows
    expect(mergeWindowLocation(saved,{...place,offset:20}).active).toBe(1)
  })
  it('updates a matching pane while preserving the other independent place',()=>{
    const saved={...state,active:0,open:[true,true]} as ReaderWindows
    const incoming={...saved.places[1],verse:17}
    const restored=mergeWindowLocation(saved,incoming)
    expect(restored.active).toBe(1);expect(restored.places[0]).toEqual(place);expect(restored.places[1]).toEqual(incoming)
    expect(saved.places[1].verse).toBe(16)
  })
  it('opens a new explicit place in the active pane without losing the other',()=>{
    const restored=mergeWindowLocation(state as ReaderWindows,{...place,book:'genesis',chapter:1})
    expect(restored.open).toEqual([true,true]);expect(restored.places[0]).toEqual(place);expect(restored.active).toBe(1)
  })
})
