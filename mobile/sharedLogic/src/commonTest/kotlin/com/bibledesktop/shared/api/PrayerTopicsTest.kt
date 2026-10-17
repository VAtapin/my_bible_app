package com.bibledesktop.shared.api
import kotlin.test.*
import kotlinx.coroutines.runBlocking
class PrayerTopicsTest {
 @Test fun topicsKeepRulesAndIndividualPrayersTogether(){
  listOf("prayer-morning-rule","prayer-morning-1","prayer-morning-trinity").forEach{assertEquals("morning",prayerTopic(it))}
  listOf("prayer-evening-rule","prayer-evening-1","prayer-before-sleep").forEach{assertEquals("evening",prayerTopic(it))}
  assertEquals("meals",prayerTopic("prayer-before-evening-meal"))
  assertEquals("study",prayerTopic("prayer-before-study"))
  assertEquals("work",prayerTopic("prayer-before-work"))
  assertEquals("communion",prayerTopic("prayer-communion-rule"))
 }
 private fun work(slug:String,collections:List<String> = listOf("prayers"))=LiturgicalWorkSummary(1,slug,slug,collections,listOf("de"),listOf(LiturgicalEditionSummary("actual","Actual","de","modern","full")))
 @Test fun catalogUsesConfiguredLanguagesAndOnePreferredGermanEdition(){
  val editions=listOf(LiturgicalEditionSummary("maltzew","Maltzew","de","historic-1911","full"),LiturgicalEditionSummary("orthodoxia","Orthodoxia","de","modern-de","full"))
  val prayer=work("prayer-morning-rule").copy(editions=editions)
  assertEquals(listOf("ru","cu"),configuredPrayerLanguages("de",listOf("ru","cu","ru")))
  assertEquals(listOf("de"),configuredPrayerLanguages("de",emptyList()))
  assertEquals(listOf("prayer-morning-rule","canon"),prayerCatalogWorks(listOf(prayer,work("canon",listOf("canons")),work("book",listOf("prayer-books")))).map{it.slug})
  assertEquals("orthodoxia",preferredPrayerEdition(prayer,"de","orthodoxia")?.code)
  assertEquals("maltzew",preferredPrayerEdition(prayer,"de","maltzew")?.code)
 }
 @Test fun everyBookUnitIsExposedOnceWithoutBookCards()=runBlocking{
  val requested=mutableListOf<String>()
  val client=object:BibleContentSource by BibleApiClient(){
   override suspend fun getLiturgicalWorks(collection:String)=listOf(work("book",listOf("prayer-books")),work("prayer-morning-rule"))
   override suspend fun getLiturgicalWork(slug:String):LiturgicalWorkSummary{requested+=slug;return work(slug)}
   override suspend fun getLiturgicalVersion(slug:String,language:String,edition:String?)=LiturgicalWorkVersion(slug,slug,language,"actual","Actual","modern","full",emptyList(),"","","a".repeat(64),"source-verified-complete",bookSections=listOf(LiturgicalBookSection("morning","Morning",works=listOf(LiturgicalBookReference("prayer-morning-rule"),LiturgicalBookReference("prayer-morning-1"))),LiturgicalBookSection("repeat","Repeat",works=listOf(LiturgicalBookReference("prayer-morning-1")))))
  }
  assertEquals(listOf("prayer-morning-rule","prayer-morning-1"),prayerSectionWorks(client).map{it.slug})
  assertEquals(listOf("prayer-morning-1"),requested)
 }
}
