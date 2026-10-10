package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.TranslationSummary

@Composable
internal fun TranslationPicker(language: String, translations: List<TranslationSummary>, selected: String,
    onSelect: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("bible-desktop-reader-controls", android.content.Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favoriteTranslations",emptySet()).orEmpty().toSet()) }
    var all by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text(localized(R.string.bible_choose_translation, language)) },
        confirmButton = { TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) } },
        text = {
            Column {
                TextButton(onClick={all=!all}) { Text(readerControlText(language,if(all) "favorites" else "all")) }
                CatalogFilters(language, translations, query, { query = it }, group, { group = it }, code, { code = it })
                val hasFavorites = translations.any { it.code in favorites }
                val visible = matchingTranslations(translations, query, group, code)
                    .filter { all || !hasFavorites || it.code in favorites }
                    .sortedWith(compareByDescending<TranslationSummary> { it.code in favorites }.thenBy { it.language.code }.thenBy { it.name })
                if (visible.isEmpty()) Text(localized(R.string.translation_not_found, language))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(visible, key = TranslationSummary::code) { item ->
                        Row(Modifier.fillMaxWidth().testTag("translation-${item.code}").selectable(selected = item.code == selected, role = Role.RadioButton,
                            onClick = { onSelect(item.code) }).padding(vertical = 8.dp)) {
                            RadioButton(selected = item.code == selected, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(item.name)
                                Text("${item.language.nativeName ?: item.language.name} · ${item.shortName ?: item.code}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick={
                                favorites=if(item.code in favorites) favorites-item.code else favorites+item.code
                                preferences.edit().putStringSet("favoriteTranslations",favorites).apply()
                            }, modifier=Modifier.semantics { contentDescription=readerControlText(language,if(item.code in favorites) "unfavorite" else "favorite") }) {
                                Text(if(item.code in favorites) "★" else "☆")
                            }
                        }
                    }
                }
            }
        })
}
