package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Collections

class PrayerCatalogUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val fixture=PrayerCatalogFixture()
    private val queries=Collections.synchronizedList(mutableListOf<String>())
    private val editions=Collections.synchronizedList(mutableListOf<String>())
    private val source=object:BibleContentSource by BibleApiClient(){
        override suspend fun getPrayerCatalog(language:String):PrayerCatalog {queries+=language;return fixture.catalog}
        override suspend fun getPrayer(id:Long,language:String):PrayerDetail {editions+=language;return fixture.details.getValue(id).also{require(it.languageCode==language)}}
        override suspend fun getPrayer(id:Long):PrayerDetail=error("Reviewed UI must request the actual edition")
    }
    private fun open(language:String){compose.setContent{BibleDesktopTheme{PrayersScreen(language,source,{})}};await("prayer-group-rules")}
    private fun await(tag:String){compose.waitUntil(10_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun count(expected:Int){compose.waitUntil(10_000){compose.onAllNodes(hasTestTag("prayer-source-count") and hasText(": $expected",substring=true)).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun ukrainianInterfaceDiscoversFourGroupsAndActualEditionsWithoutUnsupportedQueries(){
        open("uk");count(21)
        listOf("short","rules","occasions","initial").forEach{compose.onNodeWithTag("prayer-group-$it").assertExists()}
        compose.onNodeWithTag("prayer-edition-uk").assertDoesNotExist();compose.onNodeWithTag("prayer-edition-en").assertDoesNotExist()
        compose.onNodeWithTag("prayer-edition-cu").assertDoesNotExist()
        compose.onNodeWithTag("prayer-edition-cu-civil").performClick();count(20)
        compose.onNodeWithTag("prayer-edition-ru").performClick();count(1)
        compose.onNodeWithTag("prayer-edition-de").performClick();count(0)
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasTestTag("prayer-external-only"))
        compose.onNodeWithTag("prayer-external-only").assertIsDisplayed()
        assertTrue(queries.isNotEmpty());assertTrue(queries.all{it=="ru"})
    }
    @Test fun fullRuleUsesActualEditionAndKeepsDescriptionSeparateFromEntireBody(){
        open("en")
        compose.onNodeWithTag("prayer-group-rules").performClick();count(4)
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasTestTag("prayer-card-prayer-morning-rule"))
        compose.onNodeWithTag("prayer-card-prayer-morning-rule").performClick();await("prayer-body")
        val detail=fixture.details.getValue(1)
        compose.onNodeWithTag("prayer-body").assertTextEquals(detail.plainText!!)
        compose.onNodeWithTag("prayer-description").assertTextEquals(detail.intro!!)
        assertTrue(detail.plainText!!.startsWith(fixture.proof("prayer-morning-rule","first")))
        assertTrue(detail.plainText!!.endsWith(fixture.proof("prayer-morning-rule","last")))
        assertEquals(listOf("cu-civil"),editions.toList())
    }
    @Test fun lordsPrayerRemainsOneCanonicalCardInBothRelevantGroups(){
        open("ru")
        compose.onNodeWithTag("prayer-group-short").performClick();count(6)
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasTestTag("prayer-card-prayer-lords"))
        compose.onAllNodesWithTag("prayer-card-prayer-lords").assertCountEquals(1)
        compose.onNodeWithTag("daily-content-list").performScrollToIndex(0)
        compose.onNodeWithTag("prayer-group-occasions").performClick()
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasTestTag("prayer-card-prayer-lords"))
        compose.onAllNodesWithTag("prayer-card-prayer-lords").assertCountEquals(1)
    }
    @Test fun longestCompleteCommunionRuleEndsVisiblyAtLargestReaderFont(){
        open("ru")
        compose.onNodeWithTag("prayer-group-rules").performClick();count(4)
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasTestTag("prayer-card-prayer-communion-rule"))
        compose.onNodeWithTag("prayer-card-prayer-communion-rule").performClick();await("prayer-body")
        repeat(9){compose.onNodeWithContentDescription("Увеличить текст").performClick()}
        val detail=fixture.details.getValue(3)
        compose.onNodeWithTag("prayer-body").assertTextEquals(detail.plainText!!)
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("prayer-body").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertEquals(28f,layouts.single().layoutInput.style.fontSize.value,0.01f)
        assertEquals(detail.plainText!!.length,layouts.single().getLineEnd(layouts.single().lineCount-1))
        val last=fixture.proof("prayer-communion-rule","last")
        assertTrue(detail.plainText!!.endsWith(last))
        val scroll=compose.onNode(hasScrollAction())
        scroll.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,1_000_000f)}
        compose.waitUntil(10_000){
            val axis=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            kotlin.math.abs(axis.value()-axis.maxValue())<1f
        }
        val viewport=scroll.fetchSemanticsNode().boundsInRoot
        val body=compose.onNodeWithTag("prayer-body").fetchSemanticsNode()
        val layout=layouts.single()
        val lastLineTop=body.positionInRoot.y+layout.getLineTop(layout.lineCount-1)
        val lastLineBottom=body.positionInRoot.y+layout.getLineBottom(layout.lineCount-1)
        assertTrue("The final line starts inside the visible reading viewport",lastLineTop>=viewport.top-1f)
        assertTrue("The entire final line ends inside the visible reading viewport",lastLineBottom<=viewport.bottom+1f)
        assertTrue("The last line is not truncated by text layout",layout.getLineBottom(layout.lineCount-1)<=layout.size.height)

    }

}
