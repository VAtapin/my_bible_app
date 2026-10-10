import {ApiError} from '@/api/client'

/** One application queue: hidden rows unsubscribe; in-flight reads keep their slot until settled. */
export function createBackgroundReferenceQueue() {
  interface Job {read:()=>Promise<unknown>;accept:(value:unknown)=>void;due:number;attempt:number;cancelled:boolean}
  const jobs:Job[]=[]
  let active=0,nextStart=0,blockedUntil=0,timer:ReturnType<typeof setTimeout>|undefined
  const delays=[3000,9000,30000,60000]
  function pump(){
    if(timer!==undefined){clearTimeout(timer);timer=undefined}
    if(active>=2||!jobs.length)return
    jobs.sort((a,b)=>a.due-b.due)
    const wait=Math.max(jobs[0]!.due,nextStart,blockedUntil)-Date.now()
    if(wait>0){timer=setTimeout(pump,wait);return}
    const job=jobs.shift()!
    if(job.cancelled){pump();return}
    active++;nextStart=Date.now()+500
    void Promise.resolve().then(job.read).then(value=>{if(!job.cancelled)job.accept(value)}).catch(error=>{
      if(error instanceof ApiError&&error.status===429)blockedUntil=Math.max(blockedUntil,Date.now()+(error.retryAfterMs??60000))
      if(!job.cancelled){job.due=Date.now()+delays[Math.min(job.attempt++,delays.length-1)]!;jobs.push(job)}
    }).finally(()=>{active--;pump()})
    pump()
  }
  return {
    subscribe<T>(read:()=>Promise<T>,accept:(value:T)=>void){
      const job:Job={read,accept:value=>accept(value as T),due:Date.now(),attempt:0,cancelled:false}
      jobs.push(job);pump()
      return()=>{job.cancelled=true;const index=jobs.indexOf(job);if(index>=0)jobs.splice(index,1);pump()}
    },
  }
}
export const backgroundReferences=createBackgroundReferenceQueue()
