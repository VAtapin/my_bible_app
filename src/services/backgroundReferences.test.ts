import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest'
import {ApiError} from '@/api/client'
import {createBackgroundReferenceQueue} from './backgroundReferences'

describe('visible inline reference background reads',()=>{
  beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(0)})
  afterEach(()=>vi.useRealTimers())
  it('limits the whole queue to two active reads with at least 500ms between starts',async()=>{
    const queue=createBackgroundReferenceQueue(),starts:number[]=[],finish:Array<()=>void>=[]
    const read=vi.fn(()=>{starts.push(Date.now());return new Promise<void>(resolve=>finish.push(resolve))})
    for(let index=0;index<4;index++)queue.subscribe(read,()=>{})
    await vi.advanceTimersByTimeAsync(0);expect(starts).toEqual([0])
    await vi.advanceTimersByTimeAsync(500);expect(starts).toEqual([0,500])
    await vi.advanceTimersByTimeAsync(1500);expect(read).toHaveBeenCalledTimes(2)
    finish[0]!();await vi.advanceTimersByTimeAsync(0);expect(starts).toEqual([0,500,2000])
    finish[1]!();await vi.advanceTimersByTimeAsync(499);expect(read).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(1);expect(starts).toEqual([0,500,2000,2500])
    finish[2]!();finish[3]!();await vi.advanceTimersByTimeAsync(0)
  })
  it('retries failures quietly after 3/9/30/60 seconds and keeps the 60 second cap',async()=>{
    const queue=createBackgroundReferenceQueue(),starts:number[]=[]
    const read=vi.fn(async()=>{starts.push(Date.now());throw new ApiError('http','failed',500)})
    const cancel=queue.subscribe(read,vi.fn())
    await vi.advanceTimersByTimeAsync(0)
    for(const delay of [3000,9000,30000,60000,60000])await vi.advanceTimersByTimeAsync(delay)
    expect(starts).toEqual([0,3000,12000,42000,102000,162000])
    cancel();await vi.advanceTimersByTimeAsync(180000);expect(read).toHaveBeenCalledTimes(6)
  })
  it('unsubscribing hidden or replaced rows removes queued retries and ignores an in-flight old result',async()=>{
    const queue=createBackgroundReferenceQueue(),oldAccept=vi.fn(),newAccept=vi.fn()
    let resolveOld!:(value:string)=>void
    const cancelOld=queue.subscribe(()=>new Promise<string>(resolve=>resolveOld=resolve),oldAccept)
    await vi.advanceTimersByTimeAsync(0);cancelOld()
    const cancelledRead=vi.fn(async()=> 'hidden')
    const cancelQueued=queue.subscribe(cancelledRead,vi.fn());cancelQueued()
    queue.subscribe(async()=> 'new exact source',newAccept)
    resolveOld('old source');await vi.advanceTimersByTimeAsync(500)
    expect(oldAccept).not.toHaveBeenCalled();expect(cancelledRead).not.toHaveBeenCalled()
    expect(newAccept).toHaveBeenCalledWith('new exact source')
    await vi.advanceTimersByTimeAsync(120000);expect(newAccept).toHaveBeenCalledTimes(1)
  })
  it('honours a shared 429 Retry-After before any other row starts and respects longer server pauses',async()=>{
    const queue=createBackgroundReferenceQueue(),starts:number[]=[]
    const first=vi.fn(async()=>{starts.push(Date.now());if(first.mock.calls.length===1)throw new ApiError('http','rate limited',429,90000);return 'references'})
    queue.subscribe(first,()=>{})
    await vi.advanceTimersByTimeAsync(0)
    const second=vi.fn(async()=>{starts.push(Date.now());return 'other references'})
    queue.subscribe(second,()=>{})
    await vi.advanceTimersByTimeAsync(89999);expect(second).not.toHaveBeenCalled();expect(first).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1);expect(second).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(500);expect(first).toHaveBeenCalledTimes(2)
    expect(starts).toEqual([0,90000,90500])
  })
  it('does not schedule a retry when an unsubscribed in-flight read later fails',async()=>{
    const queue=createBackgroundReferenceQueue()
    let rejectRead!:(reason:unknown)=>void
    const read=vi.fn(()=>new Promise<string>((_,reject)=>rejectRead=reject))
    const cancel=queue.subscribe(read,vi.fn())
    await vi.advanceTimersByTimeAsync(0);cancel()
    rejectRead(new ApiError('offline','disconnected'))
    await vi.advanceTimersByTimeAsync(180000)
    expect(read).toHaveBeenCalledTimes(1)
  })
})
