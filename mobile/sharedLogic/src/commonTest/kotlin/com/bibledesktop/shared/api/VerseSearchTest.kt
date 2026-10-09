package com.bibledesktop.shared.api
import kotlin.test.*
class VerseSearchTest {
    @Test fun exactFormsAndPhrasesAreDifferentFromFragments() {
        assertFalse(verseMatches("богатство", "Бог", VerseSearchMatch.EXACT))
        assertTrue(verseMatches("богатство", "Бог", VerseSearchMatch.PARTIAL))
        assertTrue(verseMatches("Бог, дал мир", "бог дал", VerseSearchMatch.PHRASE))
        assertFalse(verseMatches("Бог людям дал мир", "бог дал", VerseSearchMatch.PHRASE))
        assertFalse(verseMatches("всё", "все", VerseSearchMatch.EXACT))
        assertFalse(verseMatches("λόγος", "λογος", VerseSearchMatch.EXACT))
    }
    @Test fun strongAndHighlightsUseCompleteTokens() {
        assertTrue(verseMatches("Бог", "h430", VerseSearchMatch.STRONG, "Бог H430"))
        assertFalse(verseMatches("Бог", "H43", VerseSearchMatch.STRONG, "Бог H430"))
        assertEquals(listOf(VerseSearchHighlight("Бог", true)), verseHighlights("Бог богатство", "бог", VerseSearchMatch.EXACT).filter { it.match })
    }
}
