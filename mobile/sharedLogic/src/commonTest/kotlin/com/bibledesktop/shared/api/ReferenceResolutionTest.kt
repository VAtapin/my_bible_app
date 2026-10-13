package com.bibledesktop.shared.api

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ReferenceResolutionTest {
    private val sha="a".repeat(64)
    private val state=ReferenceResolution("verified","kjv","synodal",5,"reviewed-v2",sha)
    private fun coordinate(ref:String)=ResolvedReference(ref,"exact","one-to-one","https://example.org/primary","review-5")
    private fun data():CrossReferences {
        val target=ReferenceTarget(1191,"Ps.119.1","Псалтирь 119:1","psalms",119,1,"RAW BODY")
        val ref=CrossReference(91,target,source="TSK",originalSource=OriginalReference(11910,"Ps.119.10"),originalTarget=OriginalReference(1191,"Ps.119.1"),
            resolvedSource=listOf(coordinate("Ps.118.10")),resolvedTargets=listOf(coordinate("Ps.118.1").copy(verseId=1181,bookSlug="psalms",chapterNumber=118,verseNumber=1,reference="Псалтирь 118:1",text="Блаженны непорочные в пути")),resolution=state)
        return CrossReferences(StudyVerse(11810,"Ps.118.10"),"RST",listOf(ref),ReferenceQueryResolution(2,"Ps.118.10",listOf(ReferenceQuerySource("TSK","verified",listOf("Ps.119.10"),5,"reviewed-v2",sha))))
    }
    @Test fun actualRenumberedTargetKeepsOriginalAndSurvivesOfflineSerialization() {
        val guarded=data().guardedReferences()
        assertEquals("Ps.119.1",guarded.references.single().target.osisRef)
        assertNull(guarded.references.single().target.text)
        assertEquals("Ps.118.1",referenceGroups(guarded.references).single().targets.single().osisRef)
        assertEquals("Ps.119.10",guarded.references.single().originalSource?.osisRef)
        val restored=Json.decodeFromString<CrossReferences>(Json.encodeToString(guarded)).guardedReferences()
        assertEquals("Блаженны непорочные в пути",referenceGroups(restored.references).single().targets.single().text)
        assertEquals(restored,restored.guardedReferences())
    }
    @Test fun missingQueryAdmissionCannotUseVerifiedLegacyOrResolvedText() {
        val value=data().copy(referenceResolution=null)
        val guarded=value.guardedReferences().references.single()
        assertNull(guarded.readingTarget)
        assertNull(guarded.target.text)
        assertFalse(guarded.versification.verified)
    }
    @Test fun wrongQueryChangedMapOrMissingEvidenceNeverPreview() {
        val baseline=data()
        val variants=listOf(
            baseline.copy(verse=StudyVerse(11811,"Ps.118.11")),
            baseline.copy(referenceResolution=baseline.referenceResolution!!.copy(schema=1)),
            baseline.copy(referenceResolution=baseline.referenceResolution!!.copy(sources=baseline.referenceResolution.sources.map{it.copy(mapSha256="b".repeat(64))})),
            baseline.copy(references=baseline.references.map{it.copy(resolvedSource=it.resolvedSource.map{v->v.copy(evidenceId="")})}),
            baseline.copy(references=baseline.references.map{it.copy(resolvedTargets=it.resolvedTargets.map{v->v.copy(reference=null)})}),
            baseline.copy(references=baseline.references.map{it.copy(resolvedTargets=it.resolvedTargets.map{v->v.copy(verseNumber=2)})}),
            baseline.copy(references=baseline.references.map{it.copy(resolvedTargets=it.resolvedTargets.map{v->v.copy(osisRef="Ps.999999999999999999999999.1")})}),
        )
        variants.forEach { assertNull(it.guardedReferences().references.single().readingTarget) }
    }
    @Test fun splitMergeAndPartialVerseRetainAllCandidatesWithoutChoosingOne() {
        val baseline=data()
        listOf(
            baseline.references.single().copy(resolvedTargets=listOf(coordinate("Ps.118.1"),coordinate("Ps.118.2"))),
            baseline.references.single().copy(resolvedTargets=baseline.references.single().resolvedTargets.map{it.copy(cardinality="many-to-one")}),
            baseline.references.single().copy(resolvedSource=listOf(coordinate("Ps.118.10").copy(part="a"))),
            baseline.references.single().copy(resolution=state.copy(status="ambiguous")),
        ).forEach { item ->
            val guarded=baseline.copy(references=listOf(item)).guardedReferences().references.single()
            assertEquals(item.resolvedTargets,guarded.resolvedTargets)
            assertNull(guarded.readingTarget)
            assertFalse(guarded.versification.verified)
        }
    }
    @Test fun alteredOriginalTargetIdAndMultipleOriginalSourceCandidatesCannotPromote() {
        val baseline=data()
        val mismatched=baseline.copy(references=baseline.references.map{it.copy(originalTarget=it.originalTarget!!.copy(verseId=999))})
        val multiple=baseline.copy(referenceResolution=baseline.referenceResolution!!.copy(sources=baseline.referenceResolution.sources.map{it.copy(originalOsisRefs=listOf("Ps.119.10","Ps.119.11"))}))
        for(value in listOf(mismatched,multiple))assertNull(value.guardedReferences().references.single().readingTarget)
    }
}
