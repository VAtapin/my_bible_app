import {isReferenceTarget,type StudyCrossReference,type StudyReferences,type StudyReferenceTarget} from '@/api/verseStudy'
import {exactReference,verifiedResolution,isReferenceResolutionEnvelope,isOriginalReference,isResolvedReference,isResolvedReferenceTarget} from '@/api/referenceResolution'

/** Admit only an independently resolved whole verse in the queried edition. */
export function resolvedReadingTarget(item:StudyCrossReference,value:StudyReferences):StudyReferenceTarget|undefined {
  const envelope=value.reference_resolution,resolution=item.resolution
  if(!isReferenceResolutionEnvelope(envelope)||envelope.query_osis_ref!==value.verse.osis_ref||!verifiedResolution(resolution)||!isOriginalReference(item.original_source)||!isOriginalReference(item.original_target))return
  const source=envelope.sources.find(source=>source.source===item.source)
  if(!source||source.status!=='verified'||source.map_id!==resolution.map_id||source.map_version!==resolution.map_version||source.map_sha256!==resolution.map_sha256||source.original_osis_refs.length!==1||source.original_osis_refs[0]!==item.original_source?.osis_ref)return
  if(item.original_target?.osis_ref!==item.target.osis_ref||item.original_target?.verse_id!==item.target.verse_id||item.resolved_source?.length!==1||item.resolved_targets?.length!==1)return
  const from=item.resolved_source[0]!,target=item.resolved_targets[0]!
  if(!isResolvedReference(from)||!isResolvedReferenceTarget(target)||!exactReference(from)||from.osis_ref!==envelope.query_osis_ref||!exactReference(target)||!target.book_slug?.trim()||!target.text?.trim()||!target.reference?.trim())return
  const reading={verse_id:target.verse_id,osis_ref:target.osis_ref,reference:target.reference,book_slug:target.book_slug,chapter_number:target.chapter_number,verse_number:target.verse_number,text:target.text,
    versification:{status:'verified' as const,source_profile:resolution.source_profile,edition_profile:resolution.edition_profile,map_version:resolution.map_version}}
  return isReferenceTarget(reading)?reading:undefined
}
