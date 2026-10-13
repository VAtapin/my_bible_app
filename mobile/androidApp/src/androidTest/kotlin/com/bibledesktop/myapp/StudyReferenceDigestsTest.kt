package com.bibledesktop.myapp

import com.bibledesktop.myapp.data.studySourceDigest
import com.bibledesktop.myapp.data.studyTextDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StudyReferenceDigestsTest {
    private fun edge(target:String,raw:String?=null,quote:Long?=null)=buildJsonObject {
        put("source_code","quote.tsk")
        put("source_verse",buildJsonObject { put("osis_ref","Ps.119.10") })
        put("target_verse",buildJsonObject { put("osis_ref",target) })
        put("type","parallel")
        put("metadata",buildJsonObject { put("raw_ref",raw?.let(::JsonPrimitive)?:JsonNull);put("legacy_quote_id",quote?.let(::JsonPrimitive)?:JsonNull) })
    }
    @Test fun compactUnicodeBytewiseDigestMatchesIndependentNodeFixtureAndKeepsDuplicates() {
        val rows=listOf(edge("Ps.119.1","Ссылка/α",9),edge("Gen.1.1"),edge("Gen.1.1"))
        assertEquals("68f697341f973c4d3f56f94a8632ddc6ef27d8e64d6c32bfd11fd43e07f26b61",studySourceDigest("quote.tsk","Ps.119.10",rows))
        assertEquals(studySourceDigest("quote.tsk","Ps.119.10",rows),studySourceDigest("quote.tsk","Ps.119.10",rows.reversed()))
        assertNotEquals(studySourceDigest("quote.tsk","Ps.119.10",rows),studySourceDigest("quote.tsk","Ps.119.10",rows.distinct()))
        assertTrue(runCatching { studySourceDigest("other-source","Ps.119.10",rows) }.isFailure)
    }
    @Test fun hashesMatchBackendChapterAndPublicSourceFixtureWithoutDecodingAgain() {
        assertEquals("6a896d577f3116c1293eaac02a6b5013bb95f6946422af00c90dca5ec2437bea",studyTextDigest("Слово & ещё, конец."))
        val row=buildJsonObject {
            put("source_code","legacy_quote");put("source_verse",buildJsonObject { put("osis_ref","Ps.119.1") })
            put("target_verse",buildJsonObject { put("osis_ref","Ps.119.2") });put("type","tsk")
            put("metadata",buildJsonObject { put("raw_ref","Ps 119:2");put("legacy_quote_id",7) })
        }
        assertEquals("13e95c9f384f067a8cc9abfd32ac012241aa447851f4f451fb1c0470bce3970a",studySourceDigest("legacy_quote","Ps.119.1",listOf(row)))
    }
}
