package com.bibledesktop.shared.api

import kotlin.test.*

class SourceMarkupTest {
 @Test fun ordinaryItalicIsNotAnInsertedWord(){assertTrue(parseSourceMarkup("Earth <i>was</i> empty",null).addedWords.isEmpty())}
 @Test fun actualMyBibleHeadingsAndInsertedWordsHavePlainTextOffsets(){val value=parseSourceMarkup("<pb/>Earth <i>was</i> empty<h>Others</h> Now","mybible");assertEquals("Earth was empty Now",value.text);assertEquals(listOf(SourceTextRange(6,9)),value.addedWords);assertEquals(listOf(SourceHeading("Others",15)),value.headings)}
 @Test fun footnoteMarkerIsNotInventedNoteText(){val value=parseSourceMarkup("God<f>[1]</f> speaks<n>Source explanation</n><S>430</S>.","mybible");assertEquals("God speaks.",value.text);assertEquals(listOf(SourceFootnote("[1]",null,3),SourceFootnote("*","Source explanation",10)),value.footnotes)}
 @Test fun executableBlocksAreRemovedAndParagraphsKeepAnchors(){val value=parseSourceMarkup("First<script>alert(1)</script><pb/>Next &amp; last","mybible");assertEquals("First Next & last",value.text);assertEquals(listOf(6),value.paragraphs)}
}
