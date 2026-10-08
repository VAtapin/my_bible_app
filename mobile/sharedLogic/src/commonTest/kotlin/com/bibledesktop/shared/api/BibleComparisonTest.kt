package com.bibledesktop.shared.api

import kotlin.test.*

class BibleComparisonTest {
    private val translation = TranslationSummary("A", "A", language = LanguageSummary("ru", "Русский"))
    private fun chapter(code: String, vararg refs: String) = BibleChapter(translation.copy(code = code),
        BibleBook("john", "John", chaptersCount = 21), ChapterSummary(3, refs.size), refs.mapIndexed { i, ref -> BibleVerse(i.toLong(), i + 1, ref, ref, ref) })

    @Test fun alignsCanonicalReferencesRatherThanRowPositions() {
        val rows = compareVerses(chapter("A", "John.3.1", "John.3.2", "John.3.10"), chapter("B", "John.3.1", "John.3.10"))
        assertEquals(listOf("John.3.1", "John.3.2", "John.3.10"), rows.map { it.reference })
        assertNull(rows[1].secondary)
        assertEquals("John.3.10", rows[2].secondary?.osisRef)
    }
    @Test fun preservesSecondaryOnlyVersesAndDoesNotMergeDifferentReferences() {
        val rows = compareVerses(chapter("A", "Ps.1.1"), chapter("B", "Ps.2.1"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.primary == null || it.secondary == null })
    }
    @Test fun duplicateCanonicalReferencesAreRejected() {
        assertFailsWith<IllegalArgumentException> { compareVerses(chapter("A", "John.3.1", "John.3.1"), chapter("B", "John.3.1")) }
    }
}
