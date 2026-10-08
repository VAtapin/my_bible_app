package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.bibledesktop.myapp.data.CalendarDownloads
import com.bibledesktop.myapp.ui.more.OfflinePanel
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Real WorkManager enqueue/cancel UI, debug emulator only; does not clear user jobs or content. */
class OfflinePanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = listOf("start", "language", "wifi", "id", "pack")
    private var original: Map<String, Any?> = emptyMap()
    private var ownedId: String? = null
    @Before fun prepare() {
        check(target.packageName == "com.bibledesktop.myapp.debug")
        val manager = WorkManager.getInstance(target)
        Assume.assumeTrue("Do not interfere with an existing download", manager.getWorkInfosForUniqueWork(CalendarDownloads.name).get().none { !it.state.isFinished })
        val preferences = target.getSharedPreferences(CalendarDownloads.name, Context.MODE_PRIVATE)
        original = keys.associateWith { preferences.all[it] }
    }
    @After fun restore() {
        ownedId?.let { WorkManager.getInstance(target).cancelWorkById(UUID.fromString(it)).result.get() }
        if (original.isNotEmpty()) {
            val editor = target.getSharedPreferences(CalendarDownloads.name, Context.MODE_PRIVATE).edit()
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                null -> editor.remove(key)
            } }
            check(editor.commit())
        }
    }
    @Test fun cancelShowsPreservedContentAndOffersResume() {
        compose.setContent { BibleDesktopTheme { OfflinePanel("ru") } }
        compose.onNodeWithText("Скачать 30 дней").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Отменить загрузку").fetchSemanticsNodes().isNotEmpty() }
        val preferences = target.getSharedPreferences(CalendarDownloads.name, Context.MODE_PRIVATE)
        ownedId = preferences.getString("id", null)
        assertNotNull(ownedId)
        compose.onNodeWithText("Отменить загрузку").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Продолжить").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Загрузка отменена. Уже сохранённые материалы остались на устройстве.").assertExists()
        assertEquals(ownedId, preferences.getString("id", null))
        assertNotNull(preferences.getString("pack", null))
    }
}
