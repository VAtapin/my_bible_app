package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.TranslationSummary

@Composable
internal fun TranslationPicker(language: String, translations: List<TranslationSummary>, selected: String,
    onSelect: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(localized(R.string.bible_choose_translation, language)) },
        confirmButton = { TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) } },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().testTag("translation-search"),
                    label = { Text(localized(R.string.translation_search, language)) }, singleLine = true)
                val visible = translations.filter { listOf(it.name, it.code, it.shortName.orEmpty(), it.language.name, it.language.nativeName.orEmpty()).any { text -> text.contains(query.trim(), ignoreCase = true) } }
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
                        }
                    }
                }
            }
        })
}
