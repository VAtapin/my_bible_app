package com.bibledesktop.myapp.ui.bible

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.ReaderLink
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

/** Check the exact edition/passage before changing local reading position. No edition fallback. */
@Composable
internal fun LinkedBibleReader(language: String, link: ReaderLink, client: BibleContentSource,
    onBack: () -> Unit, onDownloads: () -> Unit) {
    val context = LocalContext.current
    var catalog by remember(link) { mutableStateOf<List<TranslationSummary>?>(null) }
    var error by remember(link) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var applied by rememberSaveable(link) { mutableStateOf(false) }
    LaunchedEffect(link, retry) {
        catalog = null; error = false
        try {
            val editions = client.getTranslations()
            if (applied) {
                catalog = editions
                return@LaunchedEffect
            }
            require(editions.any { it.code == link.translationCode })
            val book = client.getBooks(link.translationCode).firstOrNull { it.slug == link.bookSlug }
                ?: error("Unknown book")
            require(link.chapter in 1..book.chaptersCount)
            val chapter = client.getChapter(link.translationCode, link.bookSlug, link.chapter)
            require(chapter.translation.code == link.translationCode && chapter.book.slug == link.bookSlug &&
                chapter.chapter.number == link.chapter && chapter.verses.isNotEmpty())
            require(link.verse == 0 || chapter.verses.any { it.number == link.verse })
            context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).edit()
                .putString("lastTranslation", link.translationCode).putString("lastBookSlug", link.bookSlug)
                .putInt("lastChapter", link.chapter).putInt("lastVerse", link.verse).apply()
            applied = true
            catalog = editions
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    if (catalog != null) BibleReader(language, catalog!!, client, onBack, onDownloads)
    else {
        BackHandler(onBack = onBack)
        Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
            ReadingHeader(localized(R.string.section_bible, language), language, onBack, onBack)
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                if (!error) CircularProgressIndicator()
                else {
                    Text(localized(R.string.reader_link_unavailable, language))
                    Button(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
                }
            }
        }
    }
}
