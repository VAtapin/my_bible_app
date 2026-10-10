package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.daily.CalendarService
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import org.junit.*
import org.junit.Assert.*

internal fun calendarAutomaticFixture(date:String,language:String,missing:Boolean=false):AutomaticCalendarServicePlan{
    fun text(id:String,selected:String,body:String):AutomaticCalendarText{
        val orthography=if(selected=="cu")"traditional" else "civil"
        return AutomaticCalendarText("Материал $id",body,"work:$id",id,selected,orthography,"edition-$id","liturgical-corpus",1,"a".repeat(64),
            listOf(CalendarTextEdition(1,selected,orthography,"edition-$id","liturgical-corpus")),
            if(selected==calendarTextPriority(language).first())"preferred"else"fallback",null,textId=id,slot="evening")
    }
    val available=listOf(text("A","cu","Господи, воззвахъ"),text("B","cu-civil","Господи, помилуй"))
    val missingText=AutomaticCalendarText("Материал C","",null,null,null,null,null,null,null,null,emptyList(),"missing","no-published-edition",textId="C",slot="matins")
    return AutomaticCalendarServicePlan(2,AutomaticCalendarPolicy,language,date,"mixed",available+if(missing)listOf(missingText)else emptyList(),emptyList()).validateAutomaticCalendar(date,language)
}

class CalendarAutomaticServiceTest{
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun realServiceComponentUsesPerItemFontsAndMissingInsteadOfManualLanguageChoice(){
        val api=BibleApiClient();val calls=mutableListOf<Pair<String,String>>()
        val source=object:BibleContentSource by api{
            override suspend fun getCalendarService(date:String,language:String):CalendarServicePlan=error("Manual edition API forbidden")
            override suspend fun getAutomaticCalendarService(date:String,calendarLanguage:String):AutomaticCalendarServicePlan{
                calls+=date to calendarLanguage;return calendarAutomaticFixture(date,calendarLanguage,true)
            }
        }
        try{
            compose.setContent{BibleDesktopTheme{Column(Modifier.verticalScroll(rememberScrollState())){CalendarService("2026-10-08","ru",source)}}}
            compose.onNodeWithTag("calendar-service-open").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("calendar-service-assignment-A").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(listOf("2026-10-08" to "ru"),calls)
            for((id,language) in listOf("A" to "cu","B" to "cu-civil")){
                compose.onNodeWithText("Материал $id").performScrollTo().performClick()
                compose.onNodeWithTag("calendar-text-dialog").assertIsDisplayed()
                val layout=mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("calendar-service-body-assignment-$id").performScrollTo().performSemanticsAction(SemanticsActions.GetTextLayoutResult){action->assertTrue(action(layout))}
                assertEquals(readingFont(language),layout.single().layoutInput.style.fontFamily)
                compose.onNodeWithTag("calendar-text-close").performClick()
                compose.onNodeWithTag("calendar-text-dialog").assertDoesNotExist()
            }
            compose.onNodeWithTag("calendar-service-missing-assignment-C").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("calendar-service-body-assignment-C").assertDoesNotExist()
            compose.onNode(hasClickAction() and hasText("Русский")).assertDoesNotExist()
        }finally{api.close()}
    }
    @Test fun sourceWithWrongDateIsRejectedBeforeAnyTextIsRendered(){
        val api=BibleApiClient();val calls=mutableListOf<String>()
        val source=object:BibleContentSource by api{
            override suspend fun getAutomaticCalendarService(date:String,calendarLanguage:String):AutomaticCalendarServicePlan{
                calls+=calendarLanguage;return calendarAutomaticFixture("2026-10-09",calendarLanguage)
            }
        }
        try{
            compose.setContent{BibleDesktopTheme{Column{CalendarService("2026-10-08","en",source)}}}
            compose.onNodeWithTag("calendar-service-open").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("calendar-service-error").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(listOf("en"),calls)
            compose.onNodeWithTag("calendar-service-assignment-A").assertDoesNotExist()
        }finally{api.close()}
    }
}
