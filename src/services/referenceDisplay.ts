import type {StudyCrossReference,StudyReferenceTarget} from '@/api/verseStudy'
import{verifiedReference}from'@/api/verseStudy'
import {referenceGroups,type ReferenceGroup} from './verseStudy'
export function referencePreviewText(target:StudyReferenceTarget,missing:string):string{return verifiedReference(target.versification)?target.text?.replace(/<[^>]*>/gu,'').replace(/\b[HG]\d{1,5}\b/gu,'').trim()||missing:''}
export function referenceSourceLabel(value:string){return value.split(' · ').filter(name=>name.trim()&&!/^(?:legacy_|[A-Za-z0-9]+_[A-Za-z0-9_.-]+$)/.test(name)).join(' · ')}
/** Identical published ranges merge; overlapping/different ranges retain their exact targets. */
export function displayReferenceGroups(references:StudyCrossReference[],sources:string[]|null=null,order:Record<string,number>={}):ReferenceGroup[]{
 const groups=referenceGroups(references.filter(ref=>sources===null||sources.includes(ref.source??'')),order)
 const merged=new Map<string,ReferenceGroup>()
 for(const group of groups){const key=JSON.stringify(group.targets.map(target=>[target.osis_ref,target.versification]));const existing=merged.get(key);if(!existing){merged.set(key,{...group,key})}else{existing.source=[...new Set([...existing.source.split(' · '),group.source].filter(Boolean))].join(' · ');existing.type=[...new Set([existing.type,group.type].filter(Boolean))].join(' · ')}}
 return[...merged.values()]
}
export function referenceCopyText(group:ReferenceGroup,translation:string,actual:Record<string,string>={}){
 const source=referenceSourceLabel(group.source)
 const preview=group.targets.map(target=>{const text=actual[target.osis_ref]??referencePreviewText(target,'');return text?`${target.verse_number} ${text}`:''}).filter(Boolean)
 return [`${group.label} · ${translation}${source?` · ${source}`:''}`,...preview].join('\n')
}