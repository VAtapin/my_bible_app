package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class NativePrayerCacheTest {
    private val fixture=PrayerCatalogFixture()
    private fun store()=OfflineStore(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"prayer-tests-${UUID.randomUUID()}"))
    private class Source(var catalog:PrayerCatalog,var detail:PrayerDetail):BibleContentSource by BibleApiClient(){
        var failure:Exception?=null
        val calls=mutableListOf<String>()
        override suspend fun getPrayerCatalog(language:String):PrayerCatalog {calls+=language;failure?.let{throw it};return catalog}
        override suspend fun getPrayer(id:Long):PrayerDetail {failure?.let{throw it};return detail.copy(id=id)}
        override suspend fun getPrayer(id:Long,language:String):PrayerDetail {failure?.let{throw it};require(language==detail.languageCode);return detail.copy(id=id)}
    }
    @Test fun fullReviewedRuleSurvivesOfflineAndLeavesOtherDataUntouched()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1))
        store.write("personal-test",String.serializer(),"personal");store.write("calendar-test",String.serializer(),"calendar");store.write("bible-test",String.serializer(),"bible")
        val cache=NativePrayerCache(source,store,{true});assertEquals(21,cache.catalog("ru").data.size)
        val detail=cache.detail(1,"cu-civil")
        assertTrue(detail.plainText!!.startsWith(fixture.proof("prayer-morning-rule","first")))
        assertTrue(detail.plainText!!.endsWith(fixture.proof("prayer-morning-rule","last")))
        assertEquals(fixture.proof("prayer-morning-rule","characters").toInt(),detail.plainText!!.length)
        source.failure=IOException("Offline")
        assertEquals(detail,NativePrayerCache(source,store,{false}).detail(1,"cu-civil"))
        listOf("personal","calendar","bible").forEach{assertEquals(it,store.read("$it-test",String.serializer()))}
        Unit
    }
    @Test fun oldNumericAliasAndNewRevisionReplaceOnlyTheirActualEdition()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true})
        cache.catalog("ru");cache.detail(900,"cu-civil")
        source.detail=source.detail.copy(contentRevision="b".repeat(64),body="<p>Updated full edition</p>",plainText="Updated full edition")
        val revised=cache.detail(900,"cu-civil") // no catalogue visit needed
        assertEquals("b".repeat(64),revised.contentRevision)
        val offline=NativePrayerCache(source,store,{false})
        assertEquals("Updated full edition",offline.detail(1,"cu-civil").plainText)
        assertEquals("prayer-morning-rule",offline.detail(900,"cu-civil").canonicalSlug)
        Unit
    }
    @Test fun missingMarkerAfterValidatedV2CatalogNeverResurrectsLegacyBody()=runBlocking {
        val store=store();store.write("prayer-catalog:ru",PrayerCatalog.serializer(),fixture.catalog)
        store.write("prayer:1",PrayerDetail.serializer(),PrayerDetail(1,"ru","morning",title="Old",body="Truncated"))
        val source=Source(fixture.catalog,fixture.details.getValue(1));source.failure=IOException("Offline")
        val cache=NativePrayerCache(source,store,{false});assertEquals(2,cache.catalog("ru").catalogVersion)
        assertTrue(runCatching{cache.detail(1)}.isFailure)
        Unit
    }
    @Test fun malformedReviewedCacheFailsClosedAndRepairsFromValidatedOnlineSource()=runBlocking {
        val store=store();store.write("prayer-catalog:ru",PrayerCatalog.serializer(),fixture.catalog.copy(groups=emptyMap()))
        val source=Source(fixture.catalog,fixture.details.getValue(1));source.failure=IOException("Offline")
        assertTrue(runCatching{NativePrayerCache(source,store,{false}).catalog("ru")}.isFailure)
        source.failure=null
        assertEquals(21,NativePrayerCache(source,store,{true}).catalog("ru").data.size)
        Unit
    }
    @Test fun transportCanUseExactReviewedCopyButContractFailureCannot()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true})
        cache.catalog("ru");val complete=cache.detail(1,"cu-civil")
        source.failure=IOException("offline");assertEquals(complete,cache.detail(1,"cu-civil"))
        source.failure=IllegalArgumentException("404 / inaccessible / contract")
        assertTrue(runCatching{cache.detail(1,"cu-civil")}.isFailure)
        source.failure=null;source.detail=source.detail.copy(canonicalSlug=null,liturgicalWorkId=null,completeness=null,reviewStatus=null)
        assertTrue(runCatching{cache.detail(1)}.isFailure)
        source.catalog=PrayerCatalog(emptyList());assertTrue(runCatching{cache.catalog("ru")}.isFailure)
        Unit
    }
    @Test fun nullableLegacyPlainBodyAndReviewedIntroWorkBeforeAndAfterReview()=runBlocking {
        val store=store();val source=Source(PrayerCatalog(emptyList()),PrayerDetail(1,"ru","common",title="Legacy",body="Full legacy body"))
        val cache=NativePrayerCache(source,store,{true});assertEquals("Full legacy body",cache.detail(1).body)
        assertEquals("Full legacy body",NativePrayerCache(source,store,{false}).detail(1).body)
        source.detail=fixture.details.getValue(1).copy(intro=null)
        assertNull(cache.detail(1,"cu-civil").intro)
        source.detail=source.detail.copy(plainText=null)
        assertTrue(runCatching{cache.detail(1,"cu-civil")}.isFailure)
        Unit
    }    @Test fun otherEditionRevisionDoesNotInvalidateActualCivilEditionAndCancellationNeverFallsBack()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true})
        cache.catalog("ru");val actual=cache.detail(1,"cu-civil")
        val sameWork=fixture.catalog.data.first().copy(languageCode="ru",availableLanguages=listOf("ru","cu-civil"),contentRevision="c".repeat(64))
        source.catalog=fixture.catalog.copy(data=listOf(sameWork))
        cache.catalog("ru")
        assertEquals(actual,NativePrayerCache(source,store,{false}).detail(1,"cu-civil"))
        source.failure=kotlinx.coroutines.CancellationException("Cancelled")
        assertTrue(runCatching{cache.detail(1,"cu-civil")}.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        Unit
    }

    @Test fun fullCompatibleCatalogWithdrawsVisibleWorksButPreservesReviewedHiddenLegacyWorks()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true})
        cache.catalog("ru");cache.detail(1,"cu-civil")
        source.detail=fixture.details.getValue(12);cache.detail(12,"cu-civil")
        source.catalog=fixture.catalog.copy(data=fixture.catalog.data.filterNot{it.canonicalSlug=="prayer-morning-rule"})
        cache.catalog("ru");source.failure=IOException("Offline")
        val offline=NativePrayerCache(source,store,{false})
        assertTrue(runCatching{offline.detail(1,"cu-civil")}.isFailure)
        assertEquals(fixture.details.getValue(12),offline.detail(12,"cu-civil"))
        Unit
    }
    @Test fun removedLanguageDoesNotWithdrawOtherActualEdition()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true})
        cache.catalog("ru");val civil=cache.detail(1,"cu-civil")
        source.detail=source.detail.copy(languageCode="ru",availableLanguages=listOf("ru","cu-civil"),contentRevision="d".repeat(64))
        cache.detail(1,"ru")
        source.catalog=fixture.catalog;cache.catalog("ru");source.failure=IOException("Offline")
        val offline=NativePrayerCache(source,store,{false})
        assertTrue(runCatching{offline.detail(1,"ru")}.isFailure)
        assertEquals(civil,offline.detail(1,"cu-civil"))
        Unit
    }
    private class UnavailableResponse(val status:Int):IllegalStateException("HTTP fixture $status")
    // Actual Ktor 404/409 classification is independently verified by shared MockEngine tests.
    private fun unavailable(error:Throwable)=error is UnavailableResponse&&error.status in listOf(404,409)
    @Test fun definitiveResponseRevokesLegacyFragmentBeforeV2AndSurvivesRestart()=runBlocking {
        for(status in listOf(404,409)){
            val store=store();val source=Source(PrayerCatalog(emptyList()),PrayerDetail(1,"ru","common",title="Legacy fragment",body="Old fragment"))
            val cache=NativePrayerCache(source,store,{true},::unavailable)
            cache.detail(1);source.failure=UnavailableResponse(status)
            assertTrue(runCatching{cache.detail(1)}.exceptionOrNull() is UnavailableResponse)
            source.failure=IOException("Offline")
            assertTrue(runCatching{NativePrayerCache(source,store,{false}).detail(1)}.isFailure)
            assertEquals("Old fragment",store.read("prayer:1",PrayerDetail.serializer())!!.body) // logical revocation, no deletion
        }
        Unit
    }
    @Test fun missingExplicitLanguageRevokesOnlyThatRequestAndSuccessfulEditionCanBeRestored()=runBlocking {
        val store=store();val source=Source(fixture.catalog,fixture.details.getValue(1));val cache=NativePrayerCache(source,store,{true},::unavailable)
        cache.catalog("ru");val civil=cache.detail(1,"cu-civil")
        source.failure=UnavailableResponse(404);assertTrue(runCatching{cache.detail(1,"de")}.isFailure)
        assertEquals(civil,NativePrayerCache(source,store,{false}).detail(1,"cu-civil"))
        assertTrue(runCatching{NativePrayerCache(source,store,{false}).detail(1,"de")}.isFailure)
        assertTrue(runCatching{cache.detail(1)}.isFailure)
        assertTrue(runCatching{NativePrayerCache(source,store,{false}).detail(1,"cu-civil")}.isFailure)
        source.failure=null;cache.detail(1,"cu-civil")
        assertEquals(civil,NativePrayerCache(source,store,{false}).detail(1,"cu-civil"))
        Unit
    }

}
