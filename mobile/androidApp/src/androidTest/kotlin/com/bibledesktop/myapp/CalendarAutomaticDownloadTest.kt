package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class AutomaticCalendarFixtureSource:BibleContentSource by BibleApiClient(){
    var offline=false
    var missingDate:String?=null
    val serviceCalls=mutableListOf<Pair<String,String>>()
    val monthCalls=mutableListOf<String>()
    var dayCalls=0
    private fun available(){if(offline)throw IOException("Network forbidden")}
    override suspend fun getCalendarDay(date:String,language:String,profile:String):CalendarDay{
        available();dayCalls++
        return CalendarDay(date,"2026-09-25","2026-04-12","fixture","published fixture",icons=listOf(
            CalendarIcon(1,"Icon fixture",imagePreviewUrl="https://bible-desktop.com/api/calendar/icons/1/images/1?preview=1",localCachingAllowed=true)))
    }
    override suspend fun getCalendarMonth(year:Int,month:Int,language:String):List<CalendarGridDay>{
        available();monthCalls+="$year:$month:$language"
        val period=YearMonth.of(year,month)
        return(1..period.lengthOfMonth()).map{number->val date=period.atDay(number);CalendarGridDay(date.toString(),date.minusDays(13).toString(),date.dayOfWeek.value,CalendarDayStyle("ordinary","#ffffff",400),"","#ffffff")}
    }
    override suspend fun getCalendarService(date:String,language:String):CalendarServicePlan=error("Manual service request forbidden")
    override suspend fun getAutomaticCalendarService(date:String,calendarLanguage:String):AutomaticCalendarServicePlan{
        available();serviceCalls+=date to calendarLanguage
        return calendarAutomaticFixture(date,calendarLanguage,date==missingDate)
    }
}
internal suspend fun saveCalendarFixturePreview(store:OfflineStore,icon:CalendarIcon){
    val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6jQsAAAAASUVORK5CYII=")
    store.saveImage(icon.imagePreviewUrl!!,png)
}

class CalendarAutomaticDownloadTest{
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val directory=File(context.cacheDir,"automatic-calendar-${UUID.randomUUID()}")
    @After fun cleanup(){directory.walkBottomUp().forEach{check(it.delete())}}
    @Test fun originalEnglishPolicyIsStoredForEveryDayAndLegacyDatesNeverSkipAutomaticData()=runBlocking{
        val start="2026-10-08";val dates=(0..29).map{LocalDate.parse(start).plusDays(it.toLong()).toString()}
        val store=OfflineStore(directory)
        store.write("pack:legacy:$start:en",ListSerializer(String.serializer()),dates)
        val source=AutomaticCalendarFixtureSource();val repository=OfflineContentRepository(source,store,{false})
        try{
            val result=CalendarDownloadEngine(repository,pause={},savePreview=::saveCalendarFixturePreview).download(start,"en","legacy"){_,_->}
            assertEquals(30,result.done);assertTrue(result.missingServices.isEmpty())
            assertEquals(dates.map{it to "en"},source.serviceCalls)
            assertEquals(listOf("2026:10:en","2026:11:en"),source.monthCalls)
            assertEquals(30,store.read(calendarWindowCheckpoint("legacy",start,"en"),ListSerializer(String.serializer()))!!.size)
            source.offline=true;val reopened=OfflineContentRepository(source,OfflineStore(directory),{false})
            for(date in dates){
                val plan=reopened.getAutomaticCalendarService(date,"en")
                assertEquals("en",plan.calendarLanguage);assertEquals(AutomaticCalendarPolicy,plan.textPolicy);assertEquals(date,plan.date)
                assertNotNull(store.image(reopened.getCalendarDay(date,"en").icons.single().imagePreviewUrl!!))
            }
            assertNull(store.read(automaticCalendarServiceKey(start,"ru"),AutomaticCalendarServicePlan.serializer()))
        }finally{repository.close()}
    }
    @Test fun cancellationResumesOnlyAuditedDaysAndMissingServiceContinuesWithoutFalseCompletion()=runBlocking{
        val start="2026-10-08";val store=OfflineStore(directory);val source=AutomaticCalendarFixtureSource()
        val repository=OfflineContentRepository(source,store,{false})
        try{
            val engine=CalendarDownloadEngine(repository,pause={},savePreview=::saveCalendarFixturePreview)
            val cancelled=runCatching{engine.download(start,"uk","resume"){done,_->if(done==2)throw CancellationException("Stopped")}}.exceptionOrNull()
            assertTrue(cancelled is CancellationException)
            assertEquals(2,store.read(calendarWindowCheckpoint("resume",start,"uk"),ListSerializer(String.serializer()))!!.size)
            source.serviceCalls.clear();source.missingDate=LocalDate.parse(start).plusDays(4).toString()
            val partial=engine.download(start,"uk","resume"){_,_->}
            assertEquals(29,partial.done);assertEquals(listOf(source.missingDate),partial.missingServices)
            assertEquals(28,source.serviceCalls.size);assertEquals(start.let{LocalDate.parse(it).plusDays(2).toString()},source.serviceCalls.first().first)
            val missing=repository.getAutomaticCalendarService(source.missingDate!!,"uk")
            assertTrue(missing.assignments.any{it.selection=="missing"&&it.text.isEmpty()})
            source.missingDate=null;source.serviceCalls.clear()
            assertEquals(30,engine.download(start,"uk","resume"){_,_->}.done)
            assertEquals(1,source.serviceCalls.size)
        }finally{repository.close()}
    }
}
