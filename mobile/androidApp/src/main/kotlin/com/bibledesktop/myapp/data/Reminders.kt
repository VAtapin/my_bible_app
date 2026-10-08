package com.bibledesktop.myapp.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bibledesktop.myapp.MainActivity
import com.bibledesktop.myapp.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.Locale

internal val reminderIds = listOf("morning", "evening", "reading", "calendar")
internal data class DailyReminder(val id: String, val enabled: Boolean, val time: String)
internal fun defaultReminders() = listOf(
    DailyReminder("morning", false, "08:00"), DailyReminder("evening", false, "21:00"),
    DailyReminder("reading", false, "12:00"), DailyReminder("calendar", false, "09:00"),
)

/** Calendar days, not 24-hour intervals: follows local time and DST changes. */
internal fun nextReminder(time: String, now: ZonedDateTime, lastFiredDate: String? = null): ZonedDateTime {
    val clock = LocalTime.parse(time)
    var date = now.toLocalDate()
    var next = date.atTime(clock).atZone(now.zone)
    if (!next.isAfter(now) || date.toString() == lastFiredDate) {
        date = date.plusDays(1)
        next = date.atTime(clock).atZone(now.zone)
    }
    return next
}

internal object ReminderStore {
    const val key = "dailyRemindersV1"
    private fun preferences(context: Context) = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    fun read(context: Context): List<DailyReminder> {
        val raw = preferences(context).getString(key, null) ?: return defaultReminders()
        val document = JSONObject(raw)
        check(document.getInt("schemaVersion") == 1)
        val entries = document.getJSONArray("entries")
        return (0 until entries.length()).map {
            val value = entries.getJSONObject(it)
            DailyReminder(value.getString("id"), value.getBoolean("enabled"), value.getString("time"))
        }.also(::validate)
    }
    private fun validate(values: List<DailyReminder>) {
        require(values.size == reminderIds.size && values.map { it.id }.toSet() == reminderIds.toSet())
        require(values.all { Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(it.time) })
    }
    @Synchronized fun save(context: Context, values: List<DailyReminder>) {
        read(context) // Unknown/corrupt data is not permission to overwrite it.
        validate(values)
        val entries = JSONArray()
        values.forEach { entries.put(JSONObject().put("id", it.id).put("enabled", it.enabled).put("time", it.time)) }
        check(preferences(context).edit().putString(key, JSONObject().put("schemaVersion", 1).put("entries", entries).toString()).commit())
    }
    fun lastFired(context: Context, id: String): String? = preferences(context).getString("reminderLastFired:$id", null)
    fun markFired(context: Context, id: String, date: String) {
        check(preferences(context).edit().putString("reminderLastFired:$id", date).commit())
    }
}

internal fun reminderTitle(id: String): Int = when (id) {
    "morning" -> R.string.reminder_morning
    "evening" -> R.string.reminder_evening
    "reading" -> R.string.section_bible
    else -> R.string.section_calendar
}

internal fun reminderDestination(id: String) = when (id) {
    "morning", "evening" -> "prayer"
    "reading" -> "bible"
    "calendar" -> "calendar"
    else -> null
}

internal object ReminderScheduler {
    const val channelId = "daily-reminders-v1"
    const val destinationExtra = "reminderDestination"
    private const val alarmAction = "com.bibledesktop.myapp.DAILY_REMINDER"
    private fun resources(context: Context): android.content.res.Resources {
        val language = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).getString("uiLanguage", "ru")!!
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
    }
    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, resources(context).getString(R.string.section_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
    fun allowed(context: Context, channel: String = channelId): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE

    fun alarmIntent(context: Context, id: String, configuredTime: String? = null): PendingIntent {
        require(id in reminderIds)
        return PendingIntent.getBroadcast(context, 2000 + reminderIds.indexOf(id),
            Intent(context, ReminderReceiver::class.java).setAction(alarmAction).putExtra("category", id).putExtra("configuredTime", configuredTime),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    @Synchronized fun reconcile(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        createChannel(context)
        ReminderStore.read(context).forEach { schedule(context, it, now) }
    }
    private fun schedule(context: Context, entry: DailyReminder, now: ZonedDateTime) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, entry.id, entry.time)
        manager.cancel(intent)
        if (entry.enabled) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            nextReminder(entry.time, now, ReminderStore.lastFired(context, entry.id)).toInstant().toEpochMilli(), intent)
        else context.getSystemService(NotificationManager::class.java).cancel(2000 + reminderIds.indexOf(entry.id))
    }
    /** No API calls or private verse/note text in notifications. */
    @Synchronized fun deliver(context: Context, id: String, now: ZonedDateTime = ZonedDateTime.now(), configuredTime: String? = null): Boolean {
        val entry = ReminderStore.read(context).firstOrNull { it.id == id && it.enabled } ?: return false
        if (configuredTime != null && entry.time != configuredTime) return false
        schedule(context, entry, now)
        val date = now.toLocalDate().toString()
        if (ReminderStore.lastFired(context, id) == date || !allowed(context)) return false
        createChannel(context)
        val target = Intent(context, MainActivity::class.java).putExtra(destinationExtra, reminderDestination(id))
        val open = PendingIntent.getActivity(context, 3000 + reminderIds.indexOf(id), target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val strings = resources(context)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_reminder).setContentTitle(strings.getString(reminderTitle(id)))
            .setContentText(strings.getString(R.string.reminder_message)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        // Permission may have been revoked since allowed(); leave the schedule intact.
        try { context.getSystemService(NotificationManager::class.java).notify(2000 + reminderIds.indexOf(id), notification) }
        catch (_: SecurityException) { return false }
        ReminderStore.markFired(context, id, date)
        schedule(context, entry, now)
        return true
    }
    fun isAlarm(intent: Intent) = intent.action == alarmAction
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val resets = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (!ReminderScheduler.isAlarm(intent) && intent.action !in resets) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ReminderScheduler.isAlarm(intent)) intent.getStringExtra("category")?.takeIf { it in reminderIds }?.let {
                    ReminderScheduler.deliver(context, it, configuredTime = intent.getStringExtra("configuredTime"))
                }
                else ReminderScheduler.reconcile(context)
            } catch (_: Exception) { android.util.Log.w("BibleDesktop", "Could not restore local reminders") }
            finally { pending.finish() }
        }
    }
}
