package com.bibledesktop.shared.api

import kotlin.test.*
import kotlinx.serialization.json.Json

class CalendarLectionaryTest {
    private val json=Json{ignoreUnknownKeys=true}
    @Test fun actualApiNumberSurvivesDeserializationAndOfflineRoundTrip(){
        val source="""{"id":"memory-day-2641:2026-10-11","title":"Ин.20:11-18","display_ref":"Ин.20:11-18","passage_ref":"John.20.11","reading":{"schemaVersion":1,"parseStatus":"parsed","numbering":"unknown","passages":[],"lectionary":{"status":"exact-reference-match","translation_mapping_verified":false,"candidates":[{"id":"John-64","book":"John","number":64,"source_variant":""}]}}}"""
        val reading=json.decodeFromString(CalendarReading.serializer(),source)
        assertEquals(64,reading.lectionaryNumber())
        assertEquals(64,json.decodeFromString(CalendarReading.serializer(),json.encodeToString(CalendarReading.serializer(),reading)).lectionaryNumber())
    }
    @Test fun absentAmbiguousInvalidAndDuplicateSourceVariants(){
        val reading=CalendarReading("actual","Title","Ref","John.20.11")
        assertNull(reading.lectionaryNumber())
        fun candidate(book:String,number:Int)=CalendarLectionaryCandidate(book,number)
        fun value(status:String,vararg candidates:CalendarLectionaryCandidate)=reading.copy(reading=CalendarNormalizedReading(1,"parsed",lectionary=CalendarLectionary(status,candidates.toList())))
        assertNull(value("unmatched").lectionaryNumber())
        assertNull(value("ambiguous",candidate("John",64)).lectionaryNumber())
        assertNull(value("exact-reference-match",candidate("John",64),candidate("Luke",64)).lectionaryNumber())
        assertNull(value("exact-reference-match",candidate("John",0)).lectionaryNumber())
        assertEquals(64,value("exact-reference-match",candidate("John",64),candidate("John",64)).lectionaryNumber())
    }
}
