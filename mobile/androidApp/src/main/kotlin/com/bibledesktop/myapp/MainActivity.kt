package com.bibledesktop.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bibledesktop.myapp.ui.setup.SetupApp
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.myapp.data.ReminderScheduler
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            try { ReminderScheduler.reconcile(applicationContext) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { android.util.Log.w("BibleDesktop", "Could not restore local reminders") }
        }
        setContent {
            BibleDesktopTheme {
                SetupApp(intent.getStringExtra(ReminderScheduler.destinationExtra))
            }
        }
    }
}
