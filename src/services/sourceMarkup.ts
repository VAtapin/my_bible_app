/** Offsets are UTF-16 indices into text. Only a declared source format enables custom tag semantics. */
export interface SourceTextRange {start:number;end:number}
export interface SourceHeading {text:string;offset:number}
export interface SourceFootnote {marker:string;text:string|null;offset:number}
export interface SourceMarkup {text:string;addedWords:SourceTextRange[];headings:SourceHeading[];footnotes:SourceFootnote[];paragraphs:number[]}
function decode(value:string){return value.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);/gi,(_full,entity:string)=>{if(entity[0]==='#'){const hex=entity[1]?.toLowerCase()==='x',point=parseInt(entity.slice(hex?2:1),hex?16:10);return point>0&&point<=0x10ffff?String.fromCodePoint(point):''}return({amp:'&',lt:'<',gt:'>',quot:'"',apos:"'",nbsp:' '} as Record<string,string>)[entity.toLowerCase()]??''})}
function compact(value:string){return decode(value.replace(/<[^>]*>/g,'')).replace(/\s+/gu,' ').trim()}
export function parseSourceMarkup(raw:string,format:string|undefined):SourceMarkup{
 const result:SourceMarkup={text:'',addedWords:[],headings:[],footnotes:[],paragraphs:[]}
 // HTML italic is ordinary typography; MyBible explicitly defines inserted words.
 if(format!=='mybible')return{...result,text:compact(raw.replace(/<(script|style|iframe|object)\b[^>]*>[\s\S]*?<\/\1\s*>/gi,''))}
 let body='',addedStart:number|undefined,capture:{tag:string;body:string;offset:number}|undefined
 const safe=raw.replace(/<(script|style|iframe|object)\b[^>]*>[\s\S]*?<\/\1\s*>/gi,'')
 for(const token of safe.match(/<[^>]*>|[^<]+|</g)??[]){
  const match=/^<\s*(\/?)\s*([a-z]+)\b[^>]*>$/i.exec(token)
  if(!match){const value=decode(token);if(capture)capture.body+=value;else body+=value;continue}
  const closing=Boolean(match[1]),tag=match[2]!.toLowerCase()
  if(capture){if(closing&&tag===capture.tag){const value=compact(capture.body);if(value){if(tag==='h')result.headings.push({text:value,offset:capture.offset});else if(tag==='f')result.footnotes.push({marker:value,text:null,offset:capture.offset});else if(tag==='n')result.footnotes.push({marker:'*',text:value,offset:capture.offset})}capture=undefined}continue}
  if(!closing&&['h','f','s','n'].includes(tag)){capture={tag,body:'',offset:body.length};continue}
  if(tag==='i'){if(!closing)addedStart=body.length;else if(addedStart!==undefined){result.addedWords.push({start:addedStart,end:body.length});addedStart=undefined}}
  if(!closing&&['p','pb','br'].includes(tag)){if(body.length){body+=' ';result.paragraphs.push(body.length)}}
 }
 // Map source offsets after whitespace normalization, preserving the plain-text anchor invariant.
 const offsets:number[]=[];let plain='',pending=false
 for(let i=0;i<body.length;i++){offsets[i]=plain.length;if(/\s/u.test(body[i]!)){pending=plain.length>0}else{if(pending){plain+=' ';pending=false}offsets[i]=plain.length;plain+=body[i]}}
 offsets[body.length]=plain.length
 const at=(offset:number)=>offsets[offset]??plain.length
 result.text=plain;result.addedWords=result.addedWords.map(range=>({start:at(range.start),end:at(range.end)})).filter(range=>range.end>range.start)
 result.headings=result.headings.map(item=>({...item,offset:at(item.offset)}));result.footnotes=result.footnotes.map(item=>({...item,offset:at(item.offset)}));result.paragraphs=[...new Set(result.paragraphs.map(at))].filter(offset=>offset>0&&offset<plain.length)
 return result
}
