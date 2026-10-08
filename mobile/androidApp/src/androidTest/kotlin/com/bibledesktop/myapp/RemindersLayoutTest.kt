package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.reminders.RemindersScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Rule
import org.junit.Test

class RemindersLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun phone() = checkLayout("phone", 390, 844)
    @Test fun portraitTablet() = checkLayout("portrait", 960, 1280)
    @Test fun landscapeTablet() = checkLayout("landscape", 1280, 800)
    private fun checkLayout(name: String, width: Int, height: Int) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme { RemindersScreen("ru") {} }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-toggle-morning").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
        compose.onNodeWithTag("reminder-toggle-morning").assertIsDisplayed()
        compose.onNodeWithContentDescription("На главную").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bibledesktop.myapp.debug")
        context.getExternalFilesDir(null)!!.resolve("reminders-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
