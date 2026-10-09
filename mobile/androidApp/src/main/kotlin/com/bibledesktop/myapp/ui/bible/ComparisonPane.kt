package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun ComparisonPane(language: String, primary: BibleChapter, catalog: List<TranslationSummary>,
    code: String, onCode: (String) -> Unit, source: BibleContentSource, fontSize: Float, initialVerse: Int, modifier: Modifier = Modifier) {
    val preferences = LocalContext.current.getSharedPreferences("bible-desktop-native-profile", android.content.Context.MODE_PRIVATE)
    var panes by rememberSaveable { mutableStateOf(preferences.getBoolean("comparePanes", false)) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var second by remember(primary.translation.code, primary.book.slug, primary.chapter.number, code) { mutableStateOf<BibleChapter?>(null) }
    var failure by remember(primary.translation.code, primary.book.slug, primary.chapter.number, code) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(primary, code, retry) {
        second = null; failure = 0
        if (code.isBlank()) return@LaunchedEffect
        try { second = loadComparison(primary, code, source) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: ComparisonUnavailable) { failure = R.string.compare_unavailable }
        catch (_: Exception) { failure = R.string.compare_error }
    }
    Column(modifier.testTag("bible-comparison")) {
        TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("compare-translation")) {
            Text(catalog.firstOrNull { it.code == code }?.name ?: localized(R.string.compare_choose, language))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !panes, onClick = { panes = false; preferences.edit().putBoolean("comparePanes", false).apply() },
                label = { Text(localized(R.string.compare_interleaved, language)) }, modifier = Modifier.weight(1f).testTag("compare-interleaved"))
            FilterChip(selected = panes, onClick = { panes = true; preferences.edit().putBoolean("comparePanes", true).apply() },
                label = { Text(localized(R.string.compare_panes, language)) }, modifier = Modifier.weight(1f).testTag("compare-panes"))
        }
        Text(localized(R.string.compare_numbering, language), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        when {
            code.isBlank() -> Text(localized(R.string.compare_choose, language), Modifier.padding(16.dp))
            failure != 0 -> Column(Modifier.padding(16.dp)) {
                Text(localized(failure, language))
                TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
            }
            second == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            else -> if (panes) ComparisonWindows(language, primary, second!!, fontSize, Modifier.weight(1f), initialVerse)
                else ComparisonRows(language, primary, second!!, fontSize, Modifier.weight(1f), initialVerse)
        }
    }
    if (choosing) TranslationPicker(language, catalog.filter { it.code != primary.translation.code }, code,
        onSelect = { onCode(it); choosing = false }, onClose = { choosing = false })
}

@Composable
internal fun ComparisonRows(language: String, primary: BibleChapter, secondary: BibleChapter, fontSize: Float,
    modifier: Modifier = Modifier, initialVerse: Int = 0) {
    val rows = remember(primary, secondary) { compareVerses(primary, secondary) }
    val state = rememberLazyListState()
    LaunchedEffect(primary.translation.code, secondary.translation.code, primary.book.slug, primary.chapter.number, initialVerse) {
        state.scrollToItem(rows.indexOfFirst { it.primary?.number == initialVerse }.coerceAtLeast(0))
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().testTag("comparison-rows"), state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = ComparedVerse::reference) { row ->
                val highlighted = initialVerse > 0 && row.primary?.number == initialVerse
                Card(Modifier.fillMaxWidth().testTag("compared-${row.reference}").semantics { selected = highlighted },
                    colors = CardDefaults.cardColors(containerColor = if (highlighted) LightBlue else androidx.compose.ui.graphics.Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val reference = row.primary?.let { "${primary.book.name} ${primary.chapter.number}:${it.number}" }
                            ?: row.secondary!!.let { "${secondary.book.name} ${secondary.chapter.number}:${it.number}" }
                        Text(reference, style = MaterialTheme.typography.labelMedium)
                        ComparedText(language, primary.translation, row.primary, fontSize)
                        HorizontalDivider()
                        ComparedText(language, secondary.translation, row.secondary, fontSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparedText(language: String, translation: TranslationSummary, verse: BibleVerse?, size: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(translation.name, style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(verse?.let { if (translation.language.code in setOf("cu", "cu-civil")) readingText(it.text) else it.plainText } ?: localized(R.string.compare_missing, language),
                fontFamily = readingFont(translation.language.code), fontSize = size.sp, lineHeight = (size * 1.55f).sp)
        }
    }
}

@Composable
internal fun ComparisonWindows(language: String, primary: BibleChapter, secondary: BibleChapter, fontSize: Float,
    modifier: Modifier = Modifier, initialVerse: Int = 0) {
    val rows = remember(primary, secondary) { compareVerses(primary, secondary) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(primary, secondary).forEachIndexed { index, chapter ->
            val state = rememberLazyListState()
            LaunchedEffect(primary, secondary, initialVerse) {
                state.scrollToItem(rows.indexOfFirst { it.primary?.number == initialVerse }.coerceAtLeast(0))
            }
            Card(Modifier.weight(1f).fillMaxWidth().testTag("comparison-pane-$index")) {
                Text("${chapter.translation.name} · ${chapter.book.name} ${chapter.chapter.number}",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("comparison-list-$index"), state = state,
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rows, key = ComparedVerse::reference) { row ->
                        val verse = if (index == 0) row.primary else row.secondary
                        val highlighted = initialVerse > 0 && row.primary?.number == initialVerse
                        Surface(color = if (highlighted) LightBlue else androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.fillMaxWidth().testTag("pane-$index-${row.reference}").semantics { selected = highlighted }) {
                            Column(Modifier.padding(8.dp)) {
                                Text((verse?.number ?: row.primary?.number ?: row.secondary?.number).toString(), style = MaterialTheme.typography.labelSmall)
                                SelectionContainer {
                                    Text(verse?.let { if (chapter.translation.language.code in setOf("cu", "cu-civil")) readingText(it.text) else it.plainText }
                                        ?: localized(R.string.compare_missing, language), fontFamily = readingFont(chapter.translation.language.code),
                                        fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
