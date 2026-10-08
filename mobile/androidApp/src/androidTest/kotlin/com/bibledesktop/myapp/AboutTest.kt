package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.more.AboutScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun russianPhoneShowsIdentityVersionAndRealSiteTargets() = checkPage("ru", "О приложении", "Версия", "На главную", DpSize(390.dp, 844.dp))
    @Test fun germanTabletShowsLocalizedAbout() = checkPage("de", "Über die App", "Version", "Zur Startseite", DpSize(960.dp, 1280.dp))
    @Test fun ukrainianPhoneShowsLocalizedAbout() = checkPage("uk", "Про застосунок", "Версія", "На головну", DpSize(390.dp, 844.dp))
    @Test fun englishLargeFontKeepsLinksReachable() = checkPage("en", "About the app", "Version", "Go to home", DpSize(390.dp, 844.dp), 2f)

    @OptIn(ExperimentalTestApi::class)
    private fun checkPage(language: String, title: String, versionLabel: String, homeLabel: String, size: DpSize, fontScale: Float = 1f) {
        val links = mutableListOf<String>()
        var home = false
        compose.setContent { BibleDesktopTheme {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(fontScale)) {
                AboutScreen(language, onBack = {}, onHome = { home = true }, onOpenLink = { links.add(it) })
            }
        } }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Vladimir Atapin").performScrollTo().assertIsDisplayed()
        val version = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, 0).versionName
        compose.onNodeWithText("$versionLabel $version").performScrollTo().assertIsDisplayed()
        listOf("https://bible-app.online/$language", "https://bible-desktop.com/", "https://bible-app.online/privacy").forEach { url ->
            compose.onNodeWithText(url, useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        }
        assertEquals(listOf("https://bible-app.online/$language", "https://bible-desktop.com/", "https://bible-app.online/privacy"), links)
        compose.onNodeWithContentDescription(homeLabel).performClick()
        assertTrue(home)
        if (language == "ru") {
            compose.onNodeWithText("Vladimir Atapin").performScrollTo()
            compose.waitForIdle()
            android.os.SystemClock.sleep(300)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val file = java.io.File(compose.activity.getExternalFilesDir(null), "about-phone.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
