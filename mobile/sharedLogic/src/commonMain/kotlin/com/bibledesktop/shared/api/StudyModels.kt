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

@Serializable
data class CrossReference(val id: Long, val target: ReferenceTarget)

@Serializable
data class ReferenceTarget(
    @SerialName("verse_id") val verseId: Long,
    @SerialName("osis_ref") val osisRef: String,
    val reference: String,
    @SerialName("book_slug") val bookSlug: String,
    @SerialName("chapter_number") val chapterNumber: Int,
    @SerialName("verse_number") val verseNumber: Int,
    val text: String? = null,
)

@Serializable
data class StrongTokens(val verse: StudyVerse, val tokens: List<StrongToken> = emptyList())

@Serializable
data class StrongToken(@SerialName("strong_number") val number: String)

@Serializable
data class StrongLexicon(val name: String, val language: String)

@Serializable
data class StrongEntry(
    val number: String, val word: String? = null, val transliteration: String? = null,
    val content: String? = null, val lexicon: StrongLexicon,
)
