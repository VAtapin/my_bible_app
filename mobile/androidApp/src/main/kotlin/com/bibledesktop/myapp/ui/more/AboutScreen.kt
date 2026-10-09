package com.bibledesktop.myapp.ui.more

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*

/** Public project identity and real installed version; no network needed to view this page. */
@Composable
internal fun AboutScreen(language: String, onBack: () -> Unit, onHome: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var linkFailed by remember { mutableStateOf(false) }
    val open: (String) -> Unit = { url ->
        linkFailed = false
        if (onOpenLink != null) onOpenLink(url)
        else try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { linkFailed = true }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(localized(R.string.about_title, language), language, onBack, onHome)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(localized(R.string.app_name, language), color = Navy, fontFamily = ReadingSerif, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(localized(R.string.about_description, language), color = Ink)
                Text(localized(R.string.about_version, language, version), color = PrimaryBlue)
                Card(colors = CardDefaults.cardColors(containerColor = LightBlue)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(localized(R.string.about_author, language), color = PrimaryBlue)
                        Text("Vladimir Atapin", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                AboutLink(localized(R.string.about_online, language), "https://bible-app.online/${language.takeIf { it in setOf("ru", "de", "uk", "en") } ?: "ru"}", open)
                AboutLink(localized(R.string.about_website, language), "https://bible-desktop.com/", open)
                AboutLink(localized(R.string.about_privacy, language), "https://bible-app.online/android/privacy/${language.takeIf { it in setOf("ru", "de", "uk", "en") } ?: "ru"}.html", open)
                if (linkFailed) Text(localized(R.string.about_link_unavailable, language), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AboutLink(title: String, url: String, onOpen: (String) -> Unit) {
    OutlinedButton(onClick = { onOpen(url) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(url, fontSize = 12.sp)
        }
    }
}
