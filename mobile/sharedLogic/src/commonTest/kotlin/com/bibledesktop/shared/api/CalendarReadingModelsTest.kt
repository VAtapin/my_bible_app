package com.bibledesktop.shared.api

import kotlinx.serialization.json.Json
import kotlin.test.*

class CalendarReadingModelsTest {
 @Test fun normalizedReadingsPreserveAllPartsWholeChaptersAndCrossChapterBounds(){
  val reading=Json.decodeFromString(CalendarReading.serializer(),"""{"id":"reading","title":"Reading","display_ref":"published","passage_ref":"John.3.16","reading":{"schemaVersion":1,"parseStatus":"parsed","passages":[{"book":"John","start":{"chapter":3,"verse":16},"end":{"chapter":4,"verse":2}},{"book":"Luke","start":{"chapter":1,"verse":null},"end":{"chapter":1,"verse":null}}]}}""")
  assertEquals(2,reading.reading!!.passages.size)
  assertEquals(CalendarReadingPoint(4,2),reading.reading!!.passages.first().end)
  assertNull(reading.reading!!.passages.last().start.verse)
 }
 @Test fun legacyMetadataWithoutNormalizedFieldsRemainsReadable(){
  val reading=Json.decodeFromString(CalendarReading.serializer(),"""{"id":"old","title":"Reading","display_ref":"published","passage_ref":"John.3.16"}""")
  assertNull(reading.reading);assertEquals("John.3.16",reading.passageRef)
 }
}
