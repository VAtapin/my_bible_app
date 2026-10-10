package com.bibledesktop.shared.api
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
class CommentarySourceRulesTest {
 private val rules=listOf(CommentarySourceRule("old","John",StudyPosition(3,2),StudyPosition(3,5),listOf("A")),CommentarySourceRule("new","John",StudyPosition(3,4),StudyPosition(4,2),listOf("B")))
 @Test fun boundariesLatestRuleDifferentBookAndPersistence(){
  assertEquals(listOf("BOOK"),commentarySourcesAt("John",StudyPosition(3,1),listOf("BOOK"),rules));assertEquals(listOf("A"),commentarySourcesAt("John",StudyPosition(3,2),listOf("BOOK"),rules));assertEquals(listOf("B"),commentarySourcesAt("John",StudyPosition(3,4),listOf("BOOK"),rules));assertEquals(listOf("B"),commentarySourcesAt("John",StudyPosition(4,2),listOf("BOOK"),rules));assertEquals(listOf("BOOK"),commentarySourcesAt("John",StudyPosition(4,3),listOf("BOOK"),rules));assertEquals(listOf("GLOBAL"),commentarySourcesAt("Acts",StudyPosition(3,4),listOf("GLOBAL"),rules))
  val serializer=ListSerializer(CommentarySourceRule.serializer());assertEquals(rules,Json.decodeFromString(serializer,Json.encodeToString(serializer,rules)))
 }
 @Test fun onlyActualIntervalSourcesAreRequestedAndForeignEntriesExcluded()=runBlocking{
  val called=mutableListOf<List<String>>()
  val entries=loadRuleCommentaries("John",StudyPosition(3,2),StudyPosition(3,5),listOf("BOOK"),rules){_,_,sources->called+=sources;CommentaryPage("john",3,listOf(CommentaryEntry(if(sources.first()=="A")1 else 2,body="actual",chapterFrom=3,verseFrom=1,chapterTo=3,verseTo=10,moduleCode=sources.first(),moduleName="Source"),CommentaryEntry(999,body="wrong",chapterFrom=3,verseFrom=1,moduleCode="FOREIGN",moduleName="Foreign")),2)}
  assertEquals(listOf(listOf("A"),listOf("B")),called);assertEquals(listOf(1L,2L),entries.map{it.id})
 }
}
