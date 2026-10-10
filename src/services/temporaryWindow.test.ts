import{describe,it,expect}from'vitest'
import{snapshotWindows}from'./temporaryWindow'
import type{ReaderWindows}from'./readerWindows'
describe('temporary window snapshot',()=>{it('retains both offsets, editions, active pane, closed panes, sync and display mode independently',()=>{
 const state:ReaderWindows={places:[{code:'A',book:'gen',chapter:2,verse:5,offset:27},{code:'B',book:'ps',chapter:10,verse:7,offset:83}],active:1,sync:true,ratio:.7,open:[false,true]}
 const snapshot=snapshotWindows(state,'interleaved');state.places[1].code='C';state.places[0].offset=0;state.open[0]=true;state.sync=false;state.active=0
 expect(snapshot.state.places.map(p=>[p.code,p.offset])).toEqual([['A',27],['B',83]]);expect(snapshot.state.open).toEqual([false,true]);expect(snapshot.state.active).toBe(1);expect(snapshot.state.sync).toBe(true);expect(snapshot.mode).toBe('interleaved')
})})
