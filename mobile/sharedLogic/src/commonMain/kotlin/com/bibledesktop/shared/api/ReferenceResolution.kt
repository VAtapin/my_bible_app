package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class OriginalReference(@SerialName("verse_id") val verseId:Long, @SerialName("osis_ref") val osisRef:String)
@Serializable data class ReferenceResolution(
    val status:String="unknown",
    @SerialName("source_profile") val sourceProfile:String?=null,
    @SerialName("edition_profile") val editionProfile:String?=null,
    @SerialName("map_id") val mapId:Long?=null,
    @SerialName("map_version") val mapVersion:String?=null,
    @SerialName("map_sha256") val mapSha256:String?=null,
)
@Serializable data class ReferenceQuerySource(
    val source:String, val status:String="unknown",
    @SerialName("original_osis_refs") val originalOsisRefs:List<String> = emptyList(),
    @SerialName("map_id") val mapId:Long?=null,
    @SerialName("map_version") val mapVersion:String?=null,
    @SerialName("map_sha256") val mapSha256:String?=null,
)
@Serializable data class ReferenceQueryResolution(val schema:Int,
    @SerialName("query_osis_ref") val queryOsisRef:String,
    val sources:List<ReferenceQuerySource> = emptyList())
@Serializable data class ResolvedReference(
    @SerialName("osis_ref") val osisRef:String,
    val relation:String, val cardinality:String,
    @SerialName("evidence_uri") val evidenceUri:String,
    @SerialName("evidence_id") val evidenceId:String,
    val part:String?=null,
    @SerialName("verse_id") val verseId:Long?=null,
    @SerialName("book_slug") val bookSlug:String?=null,
    @SerialName("chapter_number") val chapterNumber:Int?=null,
    @SerialName("verse_number") val verseNumber:Int?=null,
    val reference:String?=null, val text:String?=null,
)

private fun String.wholeVerse()=Regex("[A-Za-z0-9]+\\.[1-9]\\d*\\.[1-9]\\d*").matches(this)
private fun ResolvedReference.exact()=osisRef.wholeVerse() && relation=="exact" && cardinality=="one-to-one" && part==null &&
    evidenceId.isNotBlank() && Regex("https?://[^/\\s@]+(?:/[^\\s]*)?").matches(evidenceUri)

/** Preserve the original audit coordinates; only an admitted one-to-one result becomes a reading target. */
internal fun CrossReference.guardedResolvedReference(data:CrossReferences):CrossReference {
    val state=resolution
    val query=data.referenceResolution
    val origin=originalSource
    val source=resolvedSource.singleOrNull()
    val result=resolvedTargets.singleOrNull()
    val admission=query?.sources?.singleOrNull { it.source==this.source }
    val mapValid=state?.status=="verified" && !state.sourceProfile.isNullOrBlank() && !state.editionProfile.isNullOrBlank() &&
        state.mapId?.let{it>0}==true && !state.mapVersion.isNullOrBlank() && state.mapSha256?.let{Regex("[a-fA-F0-9]{64}").matches(it)}==true
    val accepted=mapValid && query?.schema==2 && query.queryOsisRef==data.verse.osisRef &&
        admission?.status=="verified" && admission.mapId==state?.mapId && admission.mapVersion==state?.mapVersion && admission.mapSha256==state?.mapSha256 &&
        origin?.verseId?.let{it>0}==true && admission.originalOsisRefs.singleOrNull()==origin.osisRef && originalTarget?.verseId?.let{it>0}==true && originalTarget.verseId==target.verseId && originalTarget.osisRef==target.osisRef &&
        source?.exact()==true && source.osisRef==data.verse.osisRef && result?.exact()==true && result.verseId?.let{it>0}==true &&
        !result.bookSlug.isNullOrBlank() && !result.reference.isNullOrBlank() && !result.text.isNullOrBlank() &&
        result.chapterNumber?.let{it>0}==true && result.verseNumber?.let{it>0}==true && result.osisRef.split('.').let{it[1].toIntOrNull()==result.chapterNumber && it[2].toIntOrNull()==result.verseNumber}
    if(!accepted) return copy(target=target.copy(text=null,versification=ReferenceVersification()),versification=ReferenceVersification(),readingTarget=null)
    val verified=ReferenceVersification("verified",state!!.sourceProfile,state.editionProfile,state.mapVersion)
    return copy(target=target.copy(text=null,versification=ReferenceVersification()),readingTarget=ReferenceTarget(result!!.verseId!!,result.osisRef,result.reference!!,result.bookSlug!!,result.chapterNumber!!,result.verseNumber!!,result.text,verified),versification=verified)
}
