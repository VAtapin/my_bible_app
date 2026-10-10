package com.bibledesktop.shared.api
import kotlinx.serialization.json.Json
import kotlin.test.*
class CommentaryAnnotationsTest{
 @Test fun optionalSourceMarkupPreservesRawCanonicalLinksAndUnresolvedIncludes(){
  val value=Json.decodeFromString(CommentaryAnnotations.serializer(),"""{"source_sha256":"${"a".repeat(64)}","links":[{"kind":"bible","href":"B: 500 3:16","label":"John","status":"raw","book_slug":"john","chapter":3,"verse":16}],"media":[{"fragment_id":"drawing","module":"Actual","textual":"false","status":"unresolved"}]}""")
  assertEquals("john",value.links.single().bookSlug);assertEquals(16,value.links.single().verse);assertNull(value.media.single().src);assertEquals("drawing",value.media.single().fragmentId)
  val legacy=Json.decodeFromString(StudySection.serializer(),"""{"id":1,"chapter_from":3,"verse_from":16}""");assertNull(legacy.annotations)
 }
}
