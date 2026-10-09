package com.bibledesktop.myapp

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.more.BibleLibraryScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.io.File

class BibleLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = listOf("id", "code", "wifi")
    private var original: Map<String, Any?> = emptyMap()
    private var owned: String? = null
    private val source = BibleApiClient()
    @Before fun before() {
        check(context.packageName == "com.bibledesktop.myapp.debug")
        Assume.assumeTrue(WorkManager.getInstance(context).getWorkInfosByTag(BibleDownloads.name).get().none { !it.state.isFinished })
        val preferences = context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE)
        original = keys.associateWith { preferences.all[it] }
    }
    @After fun after() {
        owned?.let { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(it)).result.get() }
        val editor = context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE).edit()
        original.forEach { (key, value) -> when (value) { is String -> editor.putString(key, value); is Boolean -> editor.putBoolean(key, value); null -> editor.remove(key) } }
        check(editor.commit()); source.close()
    }
    @Test fun fullDownloadCanBeQueuedCancelledAndContinuedFromRealUi() {
        compose.setContent { BibleDesktopTheme { BibleLibraryScreen("ru", source, onBack = {}) } }
        compose.onNodeWithTag("library-catalog").performClick()
        compose.onNodeWithTag("translation-search").performTextInput("RST-Strong")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("install-BQ_RUSSIAN_RST_STRONG").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("install-BQ_RUSSIAN_RST_STRONG").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Отменить загрузку").fetchSemanticsNodes().isNotEmpty() }
        owned = context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE).getString("id", null)
        assertNotNull(owned)
        compose.onNodeWithText("Отменить загрузку").performScrollTo().performClick()
        compose.waitUntil(15_000) { WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(owned)).get()?.state?.isFinished == true }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Отменить загрузку").fetchSemanticsNodes().isEmpty() }
        val cancelledId = owned
        compose.onNodeWithTag("install-BQ_RUSSIAN_RST_STRONG").performScrollTo().performClick()
        compose.waitUntil(15_000) { context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE).getString("id", null) != cancelledId }
        owned = context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE).getString("id", null)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("cancel-BQ_RUSSIAN_RST_STRONG").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cancel-BQ_RUSSIAN_RST_STRONG").performScrollTo().performClick()
    }
    @Test fun unavailableChaptersRemainVisibleAndAreNeverLabelledComplete(): Unit = runBlocking {
        val root = File(context.cacheDir, "library-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val wrapped = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = root
        }
        val edition = TranslationSummary("TEST", "Тестовая редакция", language = LanguageSummary("ru", "Русский"))
        val book = BibleBook("one", "Книга", chaptersCount = 3)
        val fixture = object : BibleContentSource by source {
            override suspend fun getTranslations(language: String?) = listOf(edition)
        }
        try {
            // A UI fixture, not a claimed download or remote integration.
            OfflineStore(wrapped).write(biblePackageKey("TEST"), BiblePackage.serializer(),
                BiblePackage(edition, listOf(book), done = 2, bytes = 1234, unavailable = listOf("Книга 2")))
            compose.setContent { CompositionLocalProvider(LocalContext provides wrapped) { BibleDesktopTheme { BibleLibraryScreen("ru", fixture, onBack = {}) } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Размер и отсутствующие главы").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Размер и отсутствующие главы").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Книга 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Книга 2").assertExists()
            compose.onNodeWithText("Весь перевод сохранён и доступен без сети.").assertDoesNotExist()
            compose.onNodeWithText("Сохранено глав: 2 / 3").assertExists()
            compose.onNodeWithText("Все доступные главы сохранены. Отсутствующие главы перечислены выше.").assertExists()
            compose.onNodeWithText("Продолжить").assertExists()
            context.getExternalFilesDir(null)!!.resolve("bible-library-missing.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { root.walkBottomUp().forEach { check(it.delete()) } }
    }
}
