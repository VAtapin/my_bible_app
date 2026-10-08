package com.bibledesktop.myapp

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bibledesktop.myapp.ui.setup.SetupApp
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.myapp.data.ReminderScheduler
import com.bibledesktop.myapp.data.ReaderLink
import com.bibledesktop.myapp.data.parseReaderLink
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var readerLink by mutableStateOf<ReaderLink?>(null)
    private var destination by mutableStateOf<String?>(null)
    private var requestNumber by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNumber = savedInstanceState?.getInt("linkRequestNumber") ?: 0
        readerLink = if (savedInstanceState != null) {
            savedInstanceState.getString("readerLink")?.let { parseReaderLink(android.net.Uri.parse(it)) }
        } else if (intent.action == Intent.ACTION_VIEW) parseReaderLink(intent.data) else null
        destination = intent.getStringExtra(ReminderScheduler.destinationExtra)
        lifecycleScope.launch(Dispatchers.IO) {
            try { ReminderScheduler.reconcile(applicationContext) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { android.util.Log.w("BibleDesktop", "Could not restore local reminders") }
        }
        setContent {
            BibleDesktopTheme {
                key(requestNumber) { SetupApp(destination, readerLink) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = if (intent.action == Intent.ACTION_VIEW) parseReaderLink(intent.data) else null
        val target = intent.getStringExtra(ReminderScheduler.destinationExtra)
        if (link != null || target in setOf("bible", "prayer", "calendar")) {
            setIntent(intent)
            readerLink = link; destination = target; requestNumber++
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("linkRequestNumber", requestNumber)
        outState.putString("readerLink", readerLink?.onlineUrl())
        super.onSaveInstanceState(outState)
    }
}
