export interface ReadingLine {top:number;bottom:number}
/** Boundaries come from layout, including mixed fonts and source paragraphs. */
export function measuredPageDistance(lines:ReadingLine[],top:number,bottom:number,direction:number):number|undefined{
 const ordered=lines.filter(line=>Number.isFinite(line.top)&&line.bottom>line.top).sort((a,b)=>a.top-b.top)
 const boundary=direction>0?bottom:top-(bottom-top)
 const crossing=ordered.filter(line=>line.top<boundary&&line.bottom>boundary)
 const target=crossing.at(-1)??ordered.find(line=>line.top>=boundary)
 if(!target||direction>0&&target.top<=top||direction<0&&target.top>=top)return undefined
 return target.top-top
}
export function firstReadingLineDistance(lines:ReadingLine[],top:number,bottom:number):number{
 const first=lines.filter(line=>line.bottom>top&&line.top<bottom).sort((a,b)=>a.top-b.top)[0]
 return first?first.top-top:0
}
export function measuredReaderLines(element:HTMLElement):{lines:ReadingLine[];top:number;bottom:number}{
 const viewport=element.getBoundingClientRect();let top=viewport.top+element.clientTop
 const bottom=top+element.clientHeight
 for(const heading of element.querySelectorAll<HTMLElement>('.chapter-heading')){
  const rect=heading.getBoundingClientRect()
  if(getComputedStyle(heading).position==='sticky'&&rect.top<=top+1&&rect.bottom>top)top=Math.min(bottom,rect.bottom)
 }
 const lines:ReadingLine[]=[]
 for(const body of element.querySelectorAll<HTMLElement>('.source-verse')){
  const bounds=body.getBoundingClientRect()
  if(bounds.bottom<top-element.clientHeight||bounds.top>bottom+element.clientHeight)continue
  const walker=document.createTreeWalker(body,NodeFilter.SHOW_TEXT)
  let node:Node|null
  while((node=walker.nextNode())){
   if(!node.textContent?.trim()||node.parentElement?.closest('sup,a,[role="button"],[data-no-reader-gesture]'))continue
   const range=document.createRange();range.selectNodeContents(node)
   for(const rect of range.getClientRects())if(rect.width>0&&rect.height>0)lines.push({top:rect.top,bottom:rect.bottom})
  }
 }
 return{lines,top,bottom}
}
