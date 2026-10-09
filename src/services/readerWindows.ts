export interface WindowPlace { code: string; book: string; chapter: number; verse: number; offset: number }
export interface ReaderWindows { places: [WindowPlace,WindowPlace]; active: 0|1; sync: boolean; ratio: number; open: [boolean,boolean] }
export const windowRatio = (value:number) => Math.max(.2,Math.min(.8,Number.isFinite(value)?value:.5))
export function mergeWindowLocation(saved:ReaderWindows,incoming:WindowPlace):ReaderWindows {
  const matches=(p:WindowPlace)=>p.code===incoming.code&&p.book===incoming.book&&p.chapter===incoming.chapter
  const match=matches(saved.places[saved.active])?saved.active:saved.places.findIndex(matches)
  const active=(match>=0?match:saved.active) as 0|1
  const places:[WindowPlace,WindowPlace]=[...saved.places],open:[boolean,boolean]=[...saved.open]
  places[active]={...incoming};open[active]=true
  return {...saved,places,open,active}
}
export function readWindows(value:string|null):ReaderWindows|undefined {
  if(!value)return
  try {
    const parsed=JSON.parse(value) as ReaderWindows
    if(!Array.isArray(parsed.places)||parsed.places.length!==2||parsed.places.some(p=>!p||typeof p.code!=='string'||!p.code||typeof p.book!=='string'||!p.book||!Number.isInteger(p.chapter)||p.chapter<1||!Number.isInteger(p.verse)||p.verse<0||!Number.isFinite(p.offset)||p.offset<0))return
    if(![0,1].includes(parsed.active)||typeof parsed.sync!=='boolean'||!Array.isArray(parsed.open)||parsed.open.length!==2||parsed.open.some(v=>typeof v!=='boolean')||!parsed.open.some(Boolean))return
    parsed.ratio=windowRatio(parsed.ratio)
    return parsed
  }catch{return}
}
