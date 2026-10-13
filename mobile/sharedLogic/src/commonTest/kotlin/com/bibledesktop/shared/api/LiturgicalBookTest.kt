package com.bibledesktop.shared.api

import kotlinx.serialization.json.Json
import kotlin.test.*

class LiturgicalBookTest {
    private val json=Json{ignoreUnknownKeys=true}
    private val body="""{"slug":"book-source","title":"Book","language":"de","edition":"modern","edition_title":"Modern","orthography":"modern-de","reader_profile":"full","blocks":[{"id":"b1","kind":"heading","text":"First"},{"id":"b2","kind":"rubric","text":"Twice"}],"credit":"Source","source_url":"https://example.test/book","content_hash":"${"a".repeat(64)}","review_status":"source-verified-complete","completeness":"complete","artifactType":"source-book","bookCompleteness":"whole-published-edition","sourceBook":"book-source","printedPages":[2,3],"sourceEditionNotes":["Original note"],"bookSections":[{"id":"first","title":"First section","printed_pages":[2,3],"works":[{"slug":"prayer-unit","reused":true}],"startBlockId":"b1","endBlockId":"b2"}]}"""
    @Test fun sourceBookKeepsFullMetadataAcrossSerializationAndValidatesActualBoundaries(){
        val value=json.decodeFromString<LiturgicalWorkVersion>(body).validateLiturgicalVersion("de","modern")
        assertEquals(value,json.decodeFromString<LiturgicalWorkVersion>(json.encodeToString(value)))
        assertEquals("source-book",value.artifactType)
        assertEquals(listOf("Original note"),value.sourceEditionNotes)
        assertEquals("b2",value.bookSections.single().endBlockId)
        assertEquals("prayer-unit",value.bookSections.single().works.single().slug)
        assertFails{value.copy(bookSections=listOf(value.bookSections.single().copy(startBlockId="missing"))).validateLiturgicalVersion("de")}
        assertFails{value.copy(reviewStatus="unreviewed").validateLiturgicalVersion("de")}
        assertFails{value.validateLiturgicalVersion("de","historic")}
        assertFails{value.validateLiturgicalVersion("ru")}
    }
    @Test fun oldEditionsWithoutBookMetadataStayCompatible(){
        val value=json.decodeFromString<LiturgicalWorkVersion>(body)
        assertNotNull(value.copy(bookSections=emptyList(),artifactType=null).validateLiturgicalVersion("de"))
    }
}
