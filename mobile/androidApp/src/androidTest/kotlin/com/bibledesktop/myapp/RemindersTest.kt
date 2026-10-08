package com.bibledesktop.myapp

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.reminders.RemindersScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.*
import org.junit.Assert.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.*
import java.util.UUID

class RemindersTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val name = "reminders-test-${UUID.randomUUID()}"
    private var originalSettings: String? = null
    private var originalFired: Map<String, String?> = emptyMap()
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(unused: String, mode: Int) = target.getSharedPreferences(name, mode)
    }
    @Before fun before() {
        check(target.packageName == "com.bibledesktop.myapp.debug")
        originalSettings = target.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).getString(ReminderStore.key, null)
        originalFired = reminderIds.associateWith { ReminderStore.lastFired(target, it) }
    }
    @After fun after() {
        ReminderStore.save(context, defaultReminders())
        ReminderScheduler.reconcile(context)
        target.deleteSharedPreferences(name)
        val editor = target.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).edit()
        if (originalSettings == null) editor.remove(ReminderStore.key) else editor.putString(ReminderStore.key, originalSettings)
        originalFired.forEach { (id, value) -> if (value == null) editor.remove("reminderLastFired:$id") else editor.putString("reminderLastFired:$id", value) }
        check(editor.commit())
        ReminderScheduler.reconcile(target)
    }

    @Test fun nextDateHandlesMidnightDstAndDuplicateDelivery() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals("2026-10-09T08:00+02:00[Europe/Berlin]", nextReminder("08:00", ZonedDateTime.parse("2026-10-08T23:59:00+02:00[Europe/Berlin]")).toString())
        val spring = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.MIDNIGHT, zone)
        assertEquals(LocalTime.of(3, 30), nextReminder("02:30", spring).toLocalTime())
        val autumn = ZonedDateTime.of(LocalDate.of(2026, 10, 25), LocalTime.of(2, 0), zone)
        assertEquals(LocalDate.of(2026, 10, 26), nextReminder("02:30", autumn, "2026-10-25").toLocalDate())
    }

    @Test fun durableScheduleDoesNotOverwriteUnknownSchema() {
        assertTrue(ReminderStore.read(context).none { it.enabled })
        val saved = defaultReminders().map { if (it.id == "reading") it.copy(enabled = true, time = "15:23") else it }
        ReminderStore.save(context, saved)
        assertEquals(saved, ReminderStore.read(context))
        assertTrue(runCatching { ReminderStore.save(context, saved.map { it.copy(time = "25:00") }) }.isFailure)
        val prefs = context.getSharedPreferences("any", Context.MODE_PRIVATE)
        val future = """{"schemaVersion":2,"entries":[]}"""
        prefs.edit().putString(ReminderStore.key, future).commit()
        assertTrue(runCatching { ReminderStore.save(context, saved) }.isFailure)
        assertEquals(future, prefs.getString(ReminderStore.key, null))
        prefs.edit().remove(ReminderStore.key).commit()
    }

    @Test fun realNotificationIsOfflineDeduplicatedAndOpensTheRightScreen() {
        instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ReminderStore.save(context, defaultReminders().map { it.copy(enabled = true) })
        ReminderScheduler.reconcile(context)
        val now = ZonedDateTime.parse("2026-10-08T12:00:00+02:00[Europe/Berlin]")
        assertTrue(ReminderScheduler.deliver(context, "reading", now))
        assertFalse(ReminderScheduler.deliver(context, "reading", now))
        val manager = target.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.single { it.id == 2002 }.notification
        assertNotNull(notification.contentIntent)
        assertEquals("Библия", notification.extras.getString("android.title"))
        assertEquals("bible", reminderDestination("reading"))
        assertEquals("prayer", reminderDestination("morning"))
        assertEquals("calendar", reminderDestination("calendar"))
        assertNull(reminderDestination("invalid"))
        ReminderStore.save(context, defaultReminders())
        ReminderScheduler.reconcile(context)
        assertFalse(manager.activeNotifications.any { it.id in 2000..2003 })
        assertFalse(ReminderScheduler.deliver(context, "reading", now.plusDays(1)))
    }

    @Test fun blockedNotificationChannelIsRecognized() {
        instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ReminderStore.save(context, defaultReminders().map { it.copy(enabled = true) })
        ReminderScheduler.reconcile(context)
        val manager = target.getSystemService(NotificationManager::class.java)
        val blocked = "blocked-test-${UUID.randomUUID()}"
        try {
            manager.createNotificationChannel(android.app.NotificationChannel(blocked, "Blocked fixture", NotificationManager.IMPORTANCE_NONE))
            assertFalse(ReminderScheduler.allowed(context, blocked))
            assertNull(ReminderStore.lastFired(context, "reading"))
        } finally { manager.deleteNotificationChannel(blocked) }
    }

    @Test fun pendingIntentActuallyRunsReceiverAndPostsNotification() {
        instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ReminderStore.save(target, defaultReminders().map { it.copy(enabled = it.id == "morning") })
        target.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).edit().remove("reminderLastFired:morning").commit()
        ReminderScheduler.reconcile(target)
        ReminderScheduler.alarmIntent(target, "morning", "08:00").send()
        val manager = target.getSystemService(NotificationManager::class.java)
        compose.waitUntil(10_000) { manager.activeNotifications.any { it.id == 2000 } }
        assertNotNull(ReminderStore.lastFired(target, "morning"))
        assertFalse(ReminderScheduler.deliver(target, "morning", configuredTime = "09:00"))
    }

    @Test fun reminderScreenUsesAllFourInterfaceLanguages() {
        var language by androidx.compose.runtime.mutableStateOf("ru")
        compose.setContent { BibleDesktopTheme { RemindersScreen(language) {} } }
        for ((code, title) in listOf("ru" to "Утренняя молитва", "de" to "Morgengebet", "uk" to "Ранкова молитва", "en" to "Morning prayer")) {
            compose.runOnIdle { language = code }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).assertIsDisplayed()
            val action = when (code) { "de" -> "Zeit ändern"; "uk" -> "Змінити час"; "en" -> "Change time"; else -> "Изменить время" }
            compose.onNodeWithTag("reminder-time-morning").assertTextContains(action, substring = true)
        }
    }

    @Test fun settingsHaveFourCategoriesAndSaveDisabledSchedule() {
        compose.setContent { BibleDesktopTheme { RemindersScreen("ru") {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-toggle-calendar").fetchSemanticsNodes().isNotEmpty() }
        reminderIds.forEach { compose.onNodeWithTag("reminder-toggle-$it").performScrollTo().assertIsOff() }
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).contains(ReminderStore.key) }
        compose.onNodeWithTag("reminders-list").performScrollToNode(hasText("Расписание сохранено."))
        compose.onNodeWithText("Расписание сохранено.").assertIsDisplayed()
        assertTrue(ReminderStore.read(compose.activity).none { it.enabled })
    }

    @Test fun explicitTimeButtonOpensNativePickerAndSavesNewTime() {
        ReminderStore.save(target, defaultReminders())
        compose.setContent { BibleDesktopTheme { RemindersScreen("ru") {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-time-morning").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reminder-time-morning").assertTextContains("08:00 · Изменить время").performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TimePicker::class.java))
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TimePicker::class.java)
                override fun getDescription() = "Set native time picker to 06:45"
                override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                    (view as android.widget.TimePicker).hour = 6
                    view.minute = 45
                    controller.loopMainThreadUntilIdle()
                }
            })
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.onNodeWithTag("reminder-time-morning").assertTextContains("06:45 · Изменить время")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { ReminderStore.read(target).first { it.id == "morning" }.time == "06:45" }
        assertTrue(ReminderStore.read(target).none { it.enabled })
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        target.getExternalFilesDir(null)!!.resolve("native-reminder-time.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
