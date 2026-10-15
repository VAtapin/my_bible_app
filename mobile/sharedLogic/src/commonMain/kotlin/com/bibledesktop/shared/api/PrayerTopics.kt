package com.bibledesktop.shared.api

val prayerTopics=listOf("morning","evening","communion","meals","work","study","living","departed","initial","canons","akathists","hours","hymns","occasions")
fun prayerTopic(slug:String,category:String="",collections:List<String> = emptyList(),groups:List<String> = emptyList()):String = when {
 category=="morning"||slug.startsWith("prayer-morning-")||slug=="prayer-optina"->"morning"
 category in listOf("evening","evening_rule")||slug.startsWith("prayer-evening-")||slug=="prayer-before-sleep"->"evening"
 category.startsWith("communion")||slug.contains("communion")->"communion"
 slug.contains("meal")||category in listOf("before_meal","after_meal")->"meals"
 slug.contains("study")||slug.contains("student")||slug.contains("lesson")||category=="study"->"study"
 slug.contains("work")->"work"
 slug.contains("depart")||slug.contains("usopshih")||slug.contains("razluchenii")->"departed"
 slug=="prayer-living"->"living"
 "canons" in collections||slug.startsWith("kanon-")->"canons"
 "akathists" in collections||slug.startsWith("akafist-")->"akathists"
 collections.any{it.startsWith("horologion")}||slug=="paschal-hours"->"hours"
 "calendar-reference" in collections||slug.startsWith("tropari-")||slug.startsWith("hymn-")||slug.startsWith("calendar-")->"hymns"
 "initial" in groups||slug in listOf("prayer-initial","prayer-trisagion","prayer-trinity","prayer-creed")->"initial"
 else->"occasions"
}
suspend fun prayerSectionWorks(client:BibleContentSource):List<LiturgicalWorkSummary>{
 val works=client.getLiturgicalWorks("")
 val result=works.filter{"prayer-books" !in it.collections}.associateBy{it.slug}.toMutableMap()
 for(book in works.filter{"prayer-books" in it.collections}){
  val language=book.availableLanguages.first()
  val edition=book.editions.first{it.language==language}.code
  val version=client.getLiturgicalVersion(book.slug,language,edition)
  require(version.bookSections.isNotEmpty()){"Prayer section index unavailable"}
  for(section in version.bookSections)for(unit in section.works){
   if(unit.slug !in result)result[unit.slug]=client.getLiturgicalWork(unit.slug)
  }
 }
 return result.values.toList()
}
