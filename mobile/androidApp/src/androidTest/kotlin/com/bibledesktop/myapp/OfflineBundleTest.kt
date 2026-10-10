package com.bibledesktop.myapp

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.*
import com.bibledesktop.myapp.ui.study.OfflineBundleSection
import java.io.IOException
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.PersonalStudyStore
import com.bibledesktop.myapp.ui.study.OfflineBundleSummary
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class OfflineBundleTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val target=InstrumentationRegistry.getInstrumentation().targetContext
    private val key="bundle-${UUID.randomUUID()}"
    private val root=File(target.cacheDir,key)
    private val context=object:ContextWrapper(target){override fun getSharedPreferences(name:String,mode:Int)=target.getSharedPreferences("$key-$name",mode)}
    private val translation=TranslationSummary("BUNDLE_TEST","Test Bible",language=LanguageSummary("en","English"))
    private val book=BibleBook("john","John",chaptersCount=1)
    private val chapter=BibleChapter(translation,book,ChapterSummary(1,1),listOf(BibleVerse(1,1,"John.1.1","source","source")))
    @Before fun before(){check(root.mkdirs())}
    @After fun after(){require(root.canonicalPath.startsWith(target.cacheDir.canonicalPath+File.separator));root.deleteRecursively();target.deleteSharedPreferences("$key-bible-desktop-native-profile")}
    @Test fun persistedCompositionActualReadinessDeletionAndBuiltInAtlasHaveNoNewJobs()=runBlocking {
        val cache=OfflineStore(File(root,"bibles"));val study=StudyPackageStore(context,directory=File(root,"study"));val coordinator=OfflineBundleCoordinator(context,cache,study)
        cache.write("books:BUNDLE_TEST",ListSerializer(BibleBook.serializer()),listOf(book));cache.write(chapterKey(translation.code,book.slug,1),BibleChapter.serializer(),chapter)
        cache.write(biblePackageKey(translation.code),BiblePackage.serializer(),BiblePackage(translation,listOf(book),done=1,bytes=500,complete=true))
        val(pack,zip)=CommentaryPackageMediaTest().archive(root,"BUNDLE_COMMENTARY",withMedia=false);study.install(pack,zip)
        val ids=listOf("bible:BUNDLE_TEST","study:BUNDLE_COMMENTARY",bundledAtlasId)
        val saved=coordinator.save("Custom sources",ids)
        assertEquals(JsonPrimitive(1),cache.read("offline-bundles-v1",JsonElement.serializer())!!.jsonObject["schema"])
        assertEquals(saved,OfflineBundleCoordinator(context,cache,study).bundles().single())
        val ready=coordinator.snapshot(ids,listOf(translation),listOf(pack),emptyList());assertTrue(ready.complete);assertEquals(3,ready.ready);assertEquals(0,ready.unknownSizes)
        val manager=WorkManager.getInstance(target);val before=manager.getWorkInfosByTag(BibleDownloads.name).get().map{it.id}
        coordinator.start(listOf(bundledAtlasId),emptyList(),emptyList(),true)
        assertEquals(before,manager.getWorkInfosByTag(BibleDownloads.name).get().map{it.id})
        val personal=PersonalStudy(marks=listOf(WordMark("note",translation.code,"John.1.1","source",0,6,"source",note="keep")))
        PersonalStudyStore.update(context){personal}
        coordinator.deleteContent(ids)
        assertTrue(cache.biblePackages().isEmpty());assertTrue(study.installed().isEmpty());assertNull(cache.read(chapterKey(translation.code,book.slug,1),BibleChapter.serializer()))
        assertEquals(personal,PersonalStudyStore.read(context));assertEquals(saved,coordinator.bundles().single())
        assertTrue(coordinator.snapshot(listOf(bundledAtlasId),emptyList(),emptyList(),emptyList()).complete)
        Unit
    }
    @Test fun corruptOrUnavailableBibleIsNotReadyAndUnknownSizeIsVisible()=runBlocking {
        val cache=OfflineStore(File(root,"bibles"));val study=StudyPackageStore(context,directory=File(root,"study"));val coordinator=OfflineBundleCoordinator(context,cache,study)
        cache.write(biblePackageKey(translation.code),BiblePackage.serializer(),BiblePackage(translation,listOf(book),done=1,bytes=500,complete=true))
        val corrupt=coordinator.snapshot(listOf("bible:BUNDLE_TEST"),listOf(translation),emptyList(),emptyList())
        assertFalse(corrupt.complete);assertEquals(BundleMemberState.MISSING,corrupt.members.single().state)
        cache.write(biblePackageKey(translation.code),BiblePackage.serializer(),BiblePackage(translation,listOf(book),unavailable=listOf("John 1")))
        val unavailable=coordinator.snapshot(listOf("bible:BUNDLE_TEST",bundledAtlasId),listOf(translation),emptyList(),emptyList())
        assertFalse(unavailable.complete);assertEquals(1,unavailable.ready);assertEquals(1,unavailable.unknownSizes);assertEquals(BundleMemberState.SOURCE_UNAVAILABLE,unavailable.members.first().state)
        compose.setContent{BibleDesktopTheme{OfflineBundleSummary("ru",unavailable)}}
        compose.onNodeWithText("Test Bible · Источник недоступен · 0/1").assertExists()
        compose.onNodeWithText("0 KB + Размер Библии не опубликован (1)").assertExists()
        compose.onNodeWithText("Состав: 1/2").assertExists()
        Unit
    }
    @Test fun publishedEstimateAndBothKnownRevisionsAreUsedWithoutGuessingInstalledBytes()=runBlocking {
        val cache=OfflineStore(File(root,"bibles"));val study=StudyPackageStore(context,directory=File(root,"study"));val coordinator=OfflineBundleCoordinator(context,cache,study)
        val old=translation.copy(contentRevision="old")
        cache.write(chapterKey(translation.code,book.slug,1),BibleChapter.serializer(),chapter)
        cache.write(biblePackageKey(translation.code),BiblePackage.serializer(),BiblePackage(old,listOf(book),done=1,bytes=99999,complete=true))
        val actual=translation.copy(contentRevision="new",offlineSizeEstimateBytes=2048)
        val updated=coordinator.snapshot(listOf("bible:BUNDLE_TEST"),listOf(actual),emptyList(),emptyList())
        assertEquals(BundleMemberState.UPDATABLE,updated.members.single().state);assertEquals(2048L,updated.knownBytes);assertTrue(updated.estimated);assertEquals(0,updated.unknownSizes)
        val legacy=coordinator.snapshot(listOf("bible:BUNDLE_TEST"),listOf(translation),emptyList(),emptyList())
        assertTrue(legacy.complete)
        assertTrue(runCatching{BundleItemId.parse("atlas:invented")}.isFailure)
        Unit
    }
    @Test fun malformedStoredRecipesAndMixedInvalidActionsCannotDeleteAnything()=runBlocking {
        val cache=OfflineStore(File(root,"bibles"));val study=StudyPackageStore(context,directory=File(root,"study"));val coordinator=OfflineBundleCoordinator(context,cache,study)
        val valid=OfflineBundle("legacy-valid","Atlas",listOf(bundledAtlasId))
        cache.write("offline-bundles-v1",JsonElement.serializer(),Json.parseToJsonElement("""[{"id":"legacy-valid","name":"Atlas","items":["atlas:openbible-v1"]},{"id":"bad","name":"Unsafe","items":["bible:../../escape"]},{"id":"wrong-shape","name":7,"items":["atlas:openbible-v1"]}]"""))
        assertEquals(listOf(valid),coordinator.bundles())
        assertEquals(JsonPrimitive(1),cache.read("offline-bundles-v1",JsonElement.serializer())!!.jsonObject["schema"])
        cache.write(chapterKey(translation.code,book.slug,1),BibleChapter.serializer(),chapter)
        cache.write(biblePackageKey(translation.code),BiblePackage.serializer(),BiblePackage(translation,listOf(book),done=1,complete=true))
        listOf("bible:../escape","bible:two words","study:x/y","study:x\\y","bible: leading","atlas:openbible-v2").forEach{assertTrue(runCatching{BundleItemId.parse(it)}.isFailure)}
        assertTrue(runCatching{coordinator.deleteContent(listOf("bible:BUNDLE_TEST","study:../../escape"))}.isFailure)
        assertNotNull(cache.read(chapterKey(translation.code,book.slug,1),BibleChapter.serializer()))
        assertTrue(runCatching{coordinator.save("x".repeat(81),listOf(bundledAtlasId))}.isFailure)
        assertTrue(runCatching{coordinator.save("Too many",(1..101).map{"bible:CODE$it"})}.isFailure)
        Unit
    }
    @Test fun completeBundleScreenOpensPresetPickerAndReopensSavedComposition()=runBlocking {
        val cache=OfflineStore(File(root,"bibles"));val study=StudyPackageStore(context,directory=File(root,"study"));val coordinator=OfflineBundleCoordinator(context,cache,study)
        cache.write("translations:available:",ListSerializer(TranslationSummary.serializer()),listOf(translation))
        coordinator.save("Saved atlas",listOf(bundledAtlasId))
        val delegate=BibleApiClient()
        val offline=object:BibleContentSource by delegate{override suspend fun getTranslations(language:String?):List<TranslationSummary> = throw IOException("offline fixture")}
        try{
            compose.setContent{BibleDesktopTheme{LazyColumn(Modifier.fillMaxSize()){item{OfflineBundleSection("en",emptyList(),emptyList(),true,offline,cache,coordinator)}}}}
            compose.waitUntil(10000){compose.onAllNodesWithText("Saved atlas").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Saved atlas").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("OpenBible · Ready offline").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("OpenBible · Ready offline").assertExists()
            compose.onNodeWithText("Bible and atlas").performClick()
            compose.onNodeWithText("Bible · Test Bible").performClick()
            compose.onNode(hasText("Choose contents") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("Contents: 1/2").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Contents: 1/2").assertExists()
            compose.onNodeWithText("Save bundle").performScrollTo().performClick()
            compose.waitUntil(10000){runBlocking{coordinator.bundles().any{it.name=="Bible and atlas"}}}
            assertEquals(listOf("bible:BUNDLE_TEST",bundledAtlasId),coordinator.bundles().first{it.name=="Bible and atlas"}.items)
            compose.onNodeWithText("Saved atlas").performScrollTo().performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("Contents: 1/1").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Contents: 1/1").assertExists()
            compose.onNodeWithText("Bible and Strong").performScrollTo().performClick()
            compose.onNodeWithText("No published Strong packages. Refresh the catalog after packages are published.").assertExists()
            compose.onNode(hasText("Choose contents") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        }finally{delegate.close()}
        Unit
    }
}
