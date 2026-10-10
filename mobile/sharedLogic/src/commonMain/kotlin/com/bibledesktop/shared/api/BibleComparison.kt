package com.bibledesktop.shared.api

data class ComparedVerse(val reference: String, val primary: BibleVerse?, val secondary: BibleVerse?,val secondaryChapter:BibleChapter?=null)
val canonicalReferenceComparator=Comparator<String>{a,b->
 val x=a.split('.');val y=b.split('.')
 (x.first().compareTo(y.first())).takeIf{it!=0} ?: ((x.getOrNull(1)?.toIntOrNull()?:Int.MAX_VALUE).compareTo(y.getOrNull(1)?.toIntOrNull()?:Int.MAX_VALUE)).takeIf{it!=0}
 ?: ((x.getOrNull(2)?.toIntOrNull()?:Int.MAX_VALUE).compareTo(y.getOrNull(2)?.toIntOrNull()?:Int.MAX_VALUE)).takeIf{it!=0} ?: a.compareTo(b)
}
/** Only exact references match; every secondary verse retains its actual module chapter. */
fun compareVerses(primary:BibleChapter,secondary:BibleChapter)=compareVerses(primary,listOf(secondary))
fun compareVerses(primary:BibleChapter,secondary:List<BibleChapter>):List<ComparedVerse>{
 require(primary.verses.map{it.osisRef}.distinct().size==primary.verses.size)
 val verses=secondary.flatMap{it.verses};require(verses.map{it.osisRef}.distinct().size==verses.size)
 val first=primary.verses.associateBy{it.osisRef};val second=verses.associateBy{it.osisRef};val chapters=secondary.flatMap{chapter->chapter.verses.map{it.osisRef to chapter}}.toMap()
 return (first.keys+second.keys).sortedWith(canonicalReferenceComparator).map{ComparedVerse(it,first[it],second[it],chapters[it])}
}
data class ComparisonChapterFrame(val primary:BibleChapter,val secondary:BibleChapter?,val secondaryChapters:List<BibleChapter> = listOfNotNull(secondary))
data class ComparedStreamRow(val frame:ComparisonChapterFrame,val row:ComparedVerse)
fun comparisonFrameRows(frames:List<ComparisonChapterFrame>):List<ComparedStreamRow>{
 val primaryRefs=frames.flatMap{it.primary.verses.map{v->v.osisRef}}.toSet();val seen=mutableSetOf<String>()
 return frames.flatMap{frame->compareVerses(frame.primary,frame.secondaryChapters).filter{row->
 (row.primary!=null || row.reference !in primaryRefs)&&seen.add(row.reference)
 }.map{ComparedStreamRow(frame,it)}}.sortedWith{a,b->canonicalReferenceComparator.compare(a.row.reference,b.row.reference)}
}
suspend fun loadComparisonChapters(primary:BibleChapter,code:String,source:BibleContentSource):List<BibleChapter>{
 require(code.isNotBlank()&&code!=primary.translation.code)
 val refs=primary.verses.map{it.osisRef};val locations=refs.chunked(200).flatMap{source.getVerseLocations(code,it)}
 if(locations.isEmpty())throw ComparisonUnavailable()
 require(locations.all{it.osis in refs}&&locations.map{it.osis}.distinct().size==locations.size)
 val chapters=locations.groupBy{it.book to it.chapter}.values.map{group->val location=group.first()
  source.getChapter(code,location.book,location.chapter).also{chapter->
   require(chapter.translation.code==code&&chapter.book.slug==location.book&&chapter.chapter.number==location.chapter)
   require(group.all{l->chapter.verses.any{it.osisRef==l.osis&&it.id==l.verseId&&it.number==l.verse&&it.plainText.isNotBlank()}})
  }
 }
 compareVerses(primary,chapters);return chapters
}

class ComparisonUnavailable : Exception("Canonical book or chapter unavailable in this translation")

suspend fun loadComparison(primary: BibleChapter, code: String, source: BibleContentSource): BibleChapter {
    require(code.isNotBlank() && code != primary.translation.code)
    val location = source.getVerseLocations(code, primary.verses.map { it.osisRef }).firstOrNull() ?: throw ComparisonUnavailable()
    return source.getChapter(code, location.book, location.chapter).also {
        require(it.translation.code == code && it.chapter.number == location.chapter && it.book.slug == location.book)
        require(it.verses.any { verse -> verse.osisRef == location.osis && verse.id == location.verseId })
        compareVerses(primary, it)
    }
}
