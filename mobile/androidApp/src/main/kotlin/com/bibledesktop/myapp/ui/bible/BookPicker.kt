package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.BibleBook
import com.bibledesktop.shared.api.TranslationSummary
import java.text.Normalizer
import java.util.Locale

internal fun bookSearchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ё', 'е').trim()

private fun bookGroup(book: BibleBook): String =
    book.canonicalBook?.testament?.takeIf { it in setOf("old", "new") } ?: "other"

internal fun matchingBooks(books: List<BibleBook>, query: String, testament: String): List<BibleBook> {
    val tokens = bookSearchKey(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return books.filter { book ->
        val group = bookGroup(book)
        val name = bookSearchKey(listOfNotNull(book.name, book.shortName, book.slug).joinToString(" "))
        (testament == "all" || group == testament) && tokens.all(name::contains)
    }
}

/** Selection uses the existing catalogue/repository, including its offline data. */
@Composable
internal fun BookPicker(
    language: String, translations: List<TranslationSummary>, selectedTranslationCode: String,
    books: List<BibleBook>?, error: Boolean, onTranslationChange: (String) -> Unit,
    onBookClick: (BibleBook) -> Unit, onRetry: () -> Unit, onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var testament by rememberSaveable { mutableStateOf("all") }
    var choosingTranslation by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val groups = remember(books) {
        listOf("all") + listOf("old", "new", "other").filter { group ->
            books?.any { bookGroup(it) == group } == true
        }
    }
    val activeGroup = testament.takeIf { it in groups } ?: "all"
    val visible = remember(books, query, activeGroup) { matchingBooks(books.orEmpty(), query, activeGroup) }
    val selected = translations.firstOrNull { it.code == selectedTranslationCode }
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(localized(R.string.bible_books_title, language), language, onBack, onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
                modifier = Modifier.fillMaxWidth().testTag("book-translation")
                    .clickable(enabled = translations.size > 1, role = Role.Button) { focus.clearFocus(); choosingTranslation = true }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(localized(R.string.bible_translation, language), color = PrimaryBlue, fontSize = 12.sp)
                        Text(selected?.name ?: localized(R.string.translations_title, language), color = Ink,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        selected?.let { Text(translationDescription(it), color = PrimaryBlue, fontSize = 12.sp) }
                    }
                    if (translations.size > 1) Icon(Icons.Outlined.ExpandMore, localized(R.string.bible_choose_translation, language), tint = Navy)
                }
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("book-search"), singleLine = true,
                label = { Text(localized(R.string.bible_find_book, language)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Outlined.Close, localized(R.string.bible_clear_search, language))
                } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    FilterChip(selected = group == activeGroup, modifier = Modifier.testTag("book-tab-$group"),
                        onClick = { testament = group; focus.clearFocus() },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Navy,
                            selectedLabelColor = Color.White, containerColor = Color.White, labelColor = Ink),
                        label = { Text(localized(groupTitle(group), language)) })
                }
            }
        }
        when {
            error -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(localized(R.string.bible_books_error, language))
                Button(onClick = onRetry) { Text(localized(R.string.retry, language)) }
            }
            books == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            visible.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(localized(R.string.bible_books_not_found, language), color = PrimaryBlue)
                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(localized(R.string.bible_clear_search, language)) }
            }
            else -> key(selectedTranslationCode, query, activeGroup) {
                LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), modifier = Modifier.weight(1f).testTag("book-grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sections = if (activeGroup == "all") listOf("old", "new", "other") else listOf(activeGroup)
                    sections.forEach { group ->
                        val groupBooks = visible.filter { bookGroup(it) == group }
                        if (groupBooks.isNotEmpty()) {
                            item(key = "group-$group", span = { GridItemSpan(maxLineSpan) }) {
                                Text(localized(groupTitle(group), language), Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    color = Navy, fontFamily = ReadingSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            items(groupBooks, key = BibleBook::slug) { book ->
                                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
                                    modifier = Modifier.fillMaxWidth().testTag("book-${book.slug}").clickable(role = Role.Button) {
                                        focus.clearFocus(); onBookClick(book)
                                    }) {
                                    Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(book.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                            Text(localized(R.string.bible_chapters_count, language, book.chaptersCount), color = PrimaryBlue, fontSize = 12.sp)
                                        }
                                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosingTranslation) AlertDialog(onDismissRequest = { choosingTranslation = false },
        title = { Text(localized(R.string.bible_choose_translation, language)) },
        confirmButton = { TextButton(onClick = { choosingTranslation = false }) { Text(localized(R.string.action_back, language)) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(translations, key = TranslationSummary::code) { translation ->
                    Row(Modifier.fillMaxWidth().selectable(selected = translation.code == selectedTranslationCode, role = Role.RadioButton,
                        onClick = {
                            choosingTranslation = false
                            if (translation.code != selectedTranslationCode) {
                                query = ""; testament = "all"
                                onTranslationChange(translation.code)
                            }
                        }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = translation.code == selectedTranslationCode, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(translation.name, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(translationDescription(translation), color = PrimaryBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        })
}

private fun translationDescription(translation: TranslationSummary): String =
    listOfNotNull(translation.language.nativeName ?: translation.language.name, translation.shortName).joinToString(" · ")

private fun groupTitle(group: String): Int = when (group) {
    "old" -> R.string.bible_old_testament
    "new" -> R.string.bible_new_testament
    "other" -> R.string.bible_other_books
    else -> R.string.language_all
}
