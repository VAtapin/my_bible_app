package com.bibledesktop.myapp.ui.daily

import com.bibledesktop.shared.api.*
import kotlinx.coroutines.delay

internal data class CalendarReadingRow(val chapter:BibleChapter,val verse:BibleVerse)
internal fun normalizedCalendarPassages(value:CalendarReading):List<CalendarReadingPassage>{
    val normalized=value.reading
    val parts=if(normalized!=null){
        require(normalized.schemaVersion==1&&normalized.parseStatus=="parsed")
        normalized.passages
    }else{
        // Existing exact OSIS parser only; display/title text is never interpreted.
        val point=canonicalStudyPosition(value.passageRef)
        val book=value.passageRef.substringBefore('.')
        listOf(CalendarReadingPassage(book,CalendarReadingPoint(point.chapter,point.verse),CalendarReadingPoint(point.chapter,point.verse)))
    }
    require(parts.size in 1..50)
    parts.forEach{part->
        require(Regex("[1-3]?[A-Za-z]+").matches(part.book))
        require(part.start.chapter>0&&part.end.chapter>=part.start.chapter&&part.end.chapter-part.start.chapter<=150)
        val first=part.start.verse;val last=part.end.verse
        require(first==null||first>0);require(last==null||last>0)
        if(part.start.chapter==part.end.chapter&&first!=null&&last!=null)require(last>=first)
    }
    return parts
}
/** Read actual edition chapters and retain each verse's real source chapter. */
internal suspend fun loadCalendarReading(client:BibleContentSource,translation:TranslationSummary,reading:CalendarReading,pause:suspend ()->Unit={delay(500)}):List<CalendarReadingRow>{
    val parts=normalizedCalendarPassages(reading)
    val books=client.getBooks(translation.code)
    val loaded=mutableMapOf<Pair<String,Int>,BibleChapter>()
    suspend fun chapter(book:String,number:Int):BibleChapter {
        loaded[book to number]?.let{return it}
        if(loaded.isNotEmpty())pause()
        return client.getChapter(translation.code,book,number).also{value->
            require(value.translation.code==translation.code&&value.book.slug==book&&value.chapter.number==number)
            require(value.verses.all{it.plainText.isNotBlank()})
            loaded[book to number]=value
        }
    }
    return parts.flatMap{part->
        val rows:List<CalendarReadingRow>
        val first=part.start.verse;val last=part.end.verse
        if(part.start.chapter==part.end.chapter&&first!=null&&last!=null){
            require(last-first<=1000)
            val refs=(first..last).map{"${part.book}.${part.start.chapter}.$it"}
            val locations=refs.chunked(100).flatMap{client.getVerseLocations(translation.code,it)}
            require(locations.map{it.osis}.toSet()==refs.toSet()&&locations.map{it.osis}.distinct().size==locations.size)
            rows=refs.map{ref->
                val location=locations.single{it.osis==ref}
                val source=chapter(location.book,location.chapter)
                val verse=source.verses.single{it.osisRef==ref&&it.id==location.verseId}
                CalendarReadingRow(source,verse)
            }
        }else{
            // A whole canonical chapter can span multiple module chapters. Inspect the
            // actual edition book, rather than assigning canonical numbers to its routes.
            val slug=books.singleOrNull{it.canonicalBook?.osisCode==part.book}?.slug?:client.getCanonicalSlug(requireNotNull(translation.canonCode),part.book)
            val book=books.single{it.slug==slug};require(book.chaptersCount in 1..200)
            val candidates=(1..book.chaptersCount).flatMap{number->val source=chapter(slug,number);source.verses.map{CalendarReadingRow(source,it)}}
            rows=candidates.filter{row->
                if(row.verse.osisRef.substringBefore('.')!=part.book)false else {
                    val point=canonicalStudyPosition(row.verse.osisRef)
                    point.chapter in part.start.chapter..part.end.chapter&&
                        (point.chapter!=part.start.chapter||first==null||point.verse>=first)&&
                        (point.chapter!=part.end.chapter||last==null||point.verse<=last)
                }
            }.sortedWith(compareBy({canonicalStudyPosition(it.verse.osisRef).chapter},{canonicalStudyPosition(it.verse.osisRef).verse}))
            require(rows.map{it.verse.osisRef}.distinct().size==rows.size)
            (part.start.chapter..part.end.chapter).forEach{number->require(rows.any{canonicalStudyPosition(it.verse.osisRef).chapter==number})}
            part.start.verse?.let{number->require(rows.any{it.verse.osisRef=="${part.book}.${part.start.chapter}.$number"})}
            part.end.verse?.let{number->require(rows.any{it.verse.osisRef=="${part.book}.${part.end.chapter}.$number"})}
        }
        require(rows.isNotEmpty());rows
    }
}
// Published backend engine fasting-colors.ts semantics; unknown colors are not inferred from labels.
internal fun calendarHasFast(color:String)=color.lowercase() in setOf("#dcebc9","#cce9f3","#f7e5b5","#ecdac7","#e3d5ed","#bfd9de","#f4cfaa","#c9c4d6")
