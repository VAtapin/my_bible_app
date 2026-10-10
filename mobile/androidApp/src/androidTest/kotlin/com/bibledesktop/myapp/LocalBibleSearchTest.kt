package com.bibledesktop.myapp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Real complete bundled Russian Bible, isolated storage and SQLite index; no API client. */
class LocalBibleSearchTest {
    @Test fun fullBiblePagesScopesStrongAndReopenedIndexAreConsistent() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"search-test-${UUID.randomUUID()}")
        val store=OfflineStore(File(directory,"content"))
        BundledBible.install(context,store)
        val file=File(directory,"index.sqlite")
        val index=LocalBibleSearch(store,file)
        val codes=setOf(BundledBible.code)
        assertTrue(index.prepare(BundledBible.code))
        val first=index.search(codes,"Бог",VerseSearchMatch.EXACT,VerseSearchScope.ALL)
        assertTrue(first.total>1000)
        assertEquals(50,first.results.size)
        assertTrue(first.results.all{verseMatches(it.text,"Бог",VerseSearchMatch.EXACT)})
        val morph=index.search(codes,"Бог",VerseSearchMatch.MORPHOLOGY,VerseSearchScope.ALL)
        assertTrue(morph.total>first.total)
        assertTrue(morph.results.any{Regex("\\bБога\\b",RegexOption.IGNORE_CASE).containsMatchIn(it.text)})
        assertEquals("бог",SearchStemming("ru").word("Богу"))
        assertEquals(SearchStemming("ru").word("всё"),SearchStemming("ru").word("все"))
        assertEquals(SearchStemming("de").word("Wort"),SearchStemming("de").word("Wörter"))
        assertEquals(SearchStemming("en").word("love"),SearchStemming("en").word("loving"))
        val second=index.search(codes,"Бог",VerseSearchMatch.EXACT,VerseSearchScope.ALL,offset=50)
        assertEquals(first.total,second.total)
        assertTrue(first.results.map{it.reference}.toSet().intersect(second.results.map{it.reference}.toSet()).isEmpty())
        val psalms=index.search(codes,"Бог",VerseSearchMatch.EXACT,VerseSearchScope.PSALMS)
        assertTrue(psalms.total>0)
        assertTrue(psalms.results.all{it.reference.startsWith("Ps.")})
        val nt=index.search(codes,"Бог",VerseSearchMatch.EXACT,VerseSearchScope.NEW)
        assertTrue(nt.total>0&&nt.total<first.total)
        assertTrue(nt.results.first().reference.startsWith("Matt."))
        val phrase=index.search(codes,"В начале",VerseSearchMatch.PHRASE,VerseSearchScope.ALL)
        assertTrue(phrase.results.any{it.reference=="Gen.1.1"})
        val strong=index.search(codes,"H430",VerseSearchMatch.STRONG,VerseSearchScope.ALL)
        assertTrue(strong.total>0)
        assertEquals("Gen.1.1",strong.results.first().reference)
        val stamp=file.lastModified()
        val reopened=LocalBibleSearch(OfflineStore(File(directory,"content")),file).search(codes,"Бог",VerseSearchMatch.EXACT,VerseSearchScope.ALL)
        assertEquals(stamp,file.lastModified())
        assertEquals(first,reopened)
        assertEquals(0,index.search(codes,"НеСуществующееСлово",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
    }
}
