package com.bibledesktop.shared.api

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VerseLocationTest {
    private val source = object : BibleContentSource by BibleApiClient() {
        override suspend fun getVerseLocations(translationCode: String, references: List<String>) =
            resolveStoredVerseLocations(translationCode, references, this)
        override suspend fun getBooks(translationCode: String) = listOf(BibleBook("joel-edition", "Joel", chaptersCount = 4, canonicalBook = CanonicalBookSummary("Joel", "old")))
        override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = BibleChapter(
            TranslationSummary(translationCode, translationCode, language = LanguageSummary("en", "English")),
            getBooks(translationCode).first(), ChapterSummary(chapterNumber, if(chapterNumber==3) 1 else 0),
            if(chapterNumber==3) listOf(BibleVerse(401,28,"Joel.2.28","Published","Published")) else emptyList())
    }
    @Test fun resolvesPublishedCanonicalVerseInADifferentModuleChapter() = runBlocking {
        assertEquals(listOf(VerseLocation(401,"Joel.2.28","joel-edition",3,28)), source.getVerseLocations("EDITION",listOf("Joel.2.28")))
    }
    @Test fun missingVerseNeverFallsBackToANearbyText() = runBlocking {
        assertEquals(emptyList(), source.getVerseLocations("EDITION",listOf("Joel.2.29")))
        assertEquals(emptyList(), source.getVerseLocations("EDITION",listOf("Mal.2.28")))
    }
}
