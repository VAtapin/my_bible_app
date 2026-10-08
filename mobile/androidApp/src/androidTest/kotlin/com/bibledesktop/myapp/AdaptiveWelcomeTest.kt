package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.setup.TranslationState
import com.bibledesktop.myapp.ui.setup.WelcomeScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.BibleApiClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Layout-only checks; live API/navigation are checked separately in NativeSmokeTest. */
class AdaptiveWelcomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val client = BibleApiClient()
    @After fun closeClient() = client.close()

    @Test fun phoneHeroUsesFullWidth() = checkLayout("phone", 390, 844, false)
    @Test fun portraitTabletHeroUsesFullWidth() = checkLayout("portrait", 960, 1280, false)
    @Test fun landscapeTabletHeroUsesLeftHalf() = checkLayout("landscape", 1280, 800, true)

    private fun checkLayout(name: String, width: Int, height: Int, split: Boolean) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme {
                    WelcomeScreen("ru", client, TranslationState.Loading, {}, {}, {}, {})
                }
            }
        }
        val layout = compose.onNodeWithTag(if (split) "welcome-split" else "welcome-stacked")
        layout.assertExists()
        val parentBounds = layout.fetchSemanticsNode().boundsInRoot
        val heroBounds = compose.onNodeWithTag("welcome-hero").fetchSemanticsNode().boundsInRoot
        val ratio = heroBounds.width / parentBounds.width
        assertTrue("Hero width ratio $ratio", if (split) ratio in 0.48f..0.52f else ratio in 0.98f..1.02f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bibledesktop.myapp.debug")
        context.getExternalFilesDir(null)!!.resolve("welcome-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
