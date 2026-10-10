package com.bibledesktop.myapp.ui.study
import com.bibledesktop.shared.api.*
internal data class ResolvedStudyReference(val code:String,val targets:List<ReferenceTarget>)
/** Canonical source metadata is resolved to exact actual module locations. */
internal suspend fun resolveStudyReference(client:BibleContentSource,savedCode:String,reference:DictionaryReference):ResolvedStudyReference {
 val translations=client.getTranslations()
 val edition=if(savedCode.isNotBlank())translations.firstOrNull{it.code==savedCode}else translations.firstOrNull{it.code==com.bibledesktop.myapp.data.BundledBible.code}?:translations.firstOrNull{it.isDefault}
 requireNotNull(edition)
 val osis=reference.bookOsis?.takeIf{Regex("[A-Za-z0-9]+").matches(it)}?:client.resolveCanonicalOsis(requireNotNull(edition.canonCode),reference.book)
 val book=client.getBooks(edition.code).firstOrNull{it.canonicalBook?.osisCode==osis};requireNotNull(book)
 val chapter=reference.chapter?:1;val first=reference.first?:1;val last=reference.last?:first;require(last>=first&&last-first<200)
 val refs=(first..last).map{"$osis.$chapter.$it"};val locations=client.getVerseLocations(edition.code,refs);require(locations.size==refs.size)
 val chapters=locations.map{it.book to it.chapter}.distinct().associateWith{(slug,number)->client.getChapter(edition.code,slug,number)}
 return ResolvedStudyReference(edition.code,refs.map{ref->val location=locations.first{it.osis==ref};val value=chapters.getValue(location.book to location.chapter);val verse=value.verses.first{it.id==location.verseId&&it.osisRef==ref&&it.plainText.isNotBlank()};ReferenceTarget(verse.id,ref,"${book.name} $chapter:${verse.number}",value.book.slug,value.chapter.number,verse.number,verse.plainText)})
}
