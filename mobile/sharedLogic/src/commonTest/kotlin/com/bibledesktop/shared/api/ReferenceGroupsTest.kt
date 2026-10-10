package com.bibledesktop.shared.api

import kotlin.test.*

class ReferenceGroupsTest {
    private fun ref(verse: Int, source: String = "A", book: String = "John", chapter: Int = 3) = CrossReference(verse.toLong(),
        ReferenceTarget(verse.toLong(), "$book.$chapter.$verse", "$book $chapter:$verse", book.lowercase(), chapter, verse, "Text"),
        type = "parallel", source = source, metadata = CrossReferenceMetadata(1, "Published"))
    @Test fun rangesPreserveSourceGapsAndCanonicalOrder() {
        val groups = referenceGroups(listOf(ref(3), ref(1), ref(2), ref(2), ref(5), ref(1,"B"), ref(1,"A","Gen",1)), mapOf("Gen" to 1,"John" to 43))
        assertEquals(listOf(listOf("Gen.1.1"), listOf("John.3.1","John.3.2","John.3.3"), listOf("John.3.1"), listOf("John.3.5")), groups.map { it.targets.map(ReferenceTarget::osisRef) })
        assertEquals("B", groups[2].source)
    }
    @Test fun bareNumbersRequireRealTestamentMetadata() {
        assertNull(explicitStrongNumber("430"))
        assertEquals("H430", explicitStrongNumber("430","old"))
        assertEquals("G430", explicitStrongNumber("G00430"))
    }
}
