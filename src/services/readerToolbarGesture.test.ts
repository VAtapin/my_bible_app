import { afterEach, describe, expect, it, vi } from 'vitest'
import { attachReaderToolbarGesture } from './readerToolbarGesture'
afterEach(() => vi.unstubAllGlobals())
function fixture() {
  vi.stubGlobal('window', { getSelection: () => ({ toString: () => '' }) })
  const handlers = new Map<string, (event: TouchEvent) => void>(), open = vi.fn()
  const element = { getBoundingClientRect: () => ({ top: 10, left: 0, right: 390 }), addEventListener: (name:string, fn:(e:TouchEvent)=>void) => handlers.set(name, fn), removeEventListener: (name:string) => handlers.delete(name) } as unknown as HTMLElement
  const dispose = attachReaderToolbarGesture(element, open)
  function event(name:string, x:number, y:number, fingers=1) {
    const touch = { identifier: 1, clientX:x, clientY:y }
    const preventDefault = vi.fn()
    handlers.get(name)!({ touches: name==='touchend'?[]:Array.from({length:fingers},()=>touch), changedTouches:[touch], cancelable:true, preventDefault } as unknown as TouchEvent)
    return preventDefault
  }
  return { open, event, handlers, dispose }
}
describe('reader toolbar top edge pull', () => {
  it('opens only for a single downward edge pull and removes listeners on disposal', () => {
    const f=fixture();f.event('touchstart',100,16);expect(f.event('touchmove',104,80)).toHaveBeenCalledOnce();f.event('touchend',104,80)
    expect(f.open).toHaveBeenCalledOnce();f.dispose();expect(f.handlers.size).toBe(0)
  })
  it('leaves text scrolling, short pulls and horizontal chapter gestures untouched', () => {
    const f=fixture()
    for(const [x,y,endX,endY] of [[100,90,100,190],[100,16,100,24],[100,16,200,20],[100,16,100,0],[-1,16,-1,100]]) {
      f.event('touchstart',x!,y!);expect(f.event('touchmove',endX!,endY!)).not.toHaveBeenCalled();f.event('touchend',endX!,endY!)
    }
    expect(f.open).not.toHaveBeenCalled()
  })
  it('rejects multiple fingers, cancelled gestures, selection and long holds', () => {
    const f=fixture();f.event('touchstart',100,16);f.event('touchmove',100,80,2);f.event('touchend',100,80)
    f.event('touchstart',100,16);f.event('touchcancel',100,80);f.event('touchend',100,80)
    const now=vi.spyOn(Date,'now');now.mockReturnValue(0);f.event('touchstart',100,16);now.mockReturnValue(1100);f.event('touchend',100,80);now.mockRestore()
    vi.stubGlobal('window',{getSelection:()=>({toString:()=> 'selected'})});f.event('touchstart',100,16);f.event('touchend',100,80)
    expect(f.open).not.toHaveBeenCalled()
  })
})
