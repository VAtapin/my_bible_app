package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudyVerse(val id: Long, @SerialName("osis_ref") val osisRef: String)

@Serializable
data class CrossReferences(
    val verse: StudyVerse,
    @SerialName("translation_code") val translationCode: String,
    val references: List<CrossReference> = emptyList(),
)
fun CrossReferences.guardedReferences():CrossReferences=copy(references=references.map{item->item.copy(target=item.target.copy(versification=item.versification,text=if(item.versification.verified)item.target.text else null))})

@Serializable
data class CrossReference(val id: Long, val target: ReferenceTarget,
    val type: String? = null, val source: String? = null, val metadata: CrossReferenceMetadata? = null,
    val versification:ReferenceVersification = ReferenceVersification())

@Serializable data class ReferenceVersification(val status:String="unknown",
    @SerialName("source_profile") val sourceProfile:String?=null,
    @SerialName("edition_profile") val editionProfile:String?=null,
    @SerialName("map_version") val mapVersion:String?=null){
    val verified:Boolean get()=status=="verified"&&!sourceProfile.isNullOrBlank()&&!editionProfile.isNullOrBlank()&&!mapVersion.isNullOrBlank()
}

@Serializable
data class CrossReferenceMetadata(@SerialName("legacy_quote_id") val legacyQuoteId: Long? = null,
    @SerialName("raw_ref") val rawReference: String? = null)

@Serializable
data class ReferenceTarget(
    @SerialName("verse_id") val verseId: Long,
    @SerialName("osis_ref") val osisRef: String,
    val reference: String,
    @SerialName("book_slug") val bookSlug: String,
    @SerialName("chapter_number") val chapterNumber: Int,
    @SerialName("verse_number") val verseNumber: Int,
    val text: String? = null,
    val versification:ReferenceVersification = ReferenceVersification(),
)

@Serializable
data class StrongTokens(val verse: StudyVerse, val tokens: List<StrongToken> = emptyList())

@Serializable
data class StrongToken(@SerialName("strong_number") val number: String,
    @SerialName("token_order") val order: Int? = null, @SerialName("surface_text") val surface: String? = null,
    @SerialName("grammar_code") val grammar: String? = null)

@Serializable
data class StrongLexicon(val name: String, val language: String, val code: String? = null)

@Serializable
data class StrongEntry(
    val number: String, val word: String? = null, val transliteration: String? = null,
    val content: String? = null, val lexicon: StrongLexicon,
    val pronunciation: String? = null,
    @SerialName("canonical_number")val canonicalNumber:String?=null,
    val scope:String?=null,
)

data class ReferenceGroup(val source: String, val type: String, val targets: List<ReferenceTarget>) {
    val label: String get() = targets.first().reference + if (targets.size > 1) "–${targets.last().verseNumber}" else ""
}

/** Only consecutive targets from the same published source become one range. */
fun referenceGroups(references: List<CrossReference>, bookOrder: Map<String, Int> = emptyMap()): List<ReferenceGroup> {
    val result = mutableListOf<ReferenceGroup>()
    references.groupBy { listOf(it.source.orEmpty(), it.type.orEmpty(), it.metadata?.legacyQuoteId, it.metadata?.rawReference,
        it.target.osisRef.substringBefore('.'), it.target.osisRef.split('.')[1], it.target.chapterNumber,it.versification) }.values.forEach { bucket ->
        var targets = mutableListOf<ReferenceTarget>()
        fun finish() { if (targets.isNotEmpty()) result.add(ReferenceGroup(bucket.first().source.orEmpty(), bucket.first().type.orEmpty(), targets.toList())); targets = mutableListOf() }
        bucket.distinctBy { it.target.osisRef }.sortedBy { it.target.verseNumber }.forEach { item ->
            if (targets.isNotEmpty() && item.target.verseNumber != targets.last().verseNumber + 1) finish()
            targets.add(item.target.copy(text=if(item.versification.verified)item.target.text else null,versification=item.versification))
        }
        finish()
    }
    return result.sortedWith(compareBy({ bookOrder[it.targets.first().osisRef.substringBefore('.')] ?: Int.MAX_VALUE },
        { it.targets.first().chapterNumber }, { it.targets.first().verseNumber }, { it.source }))
}

fun explicitStrongNumber(number: String, testament: String? = null): String? {
    val match = Regex("([HG]?)(\\d{1,5})", RegexOption.IGNORE_CASE).matchEntire(number.trim()) ?: return null
    val prefix = match.groupValues[1].uppercase().ifBlank { when (testament) { "old" -> "H"; "new" -> "G"; else -> "" } }
    return prefix.takeIf { it.isNotBlank() && match.groupValues[2].toInt() > 0 }?.let { "$it${match.groupValues[2].toInt()}" }
}
