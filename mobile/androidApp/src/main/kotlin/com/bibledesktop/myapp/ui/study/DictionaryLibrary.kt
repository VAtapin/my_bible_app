package com.bibledesktop.myapp.ui.study

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Scripture links use the canonical catalogue, never an edition-specific guessed slug. */
@Composable internal fun DictionaryLibrary(language: String, client: BibleContentSource, savedCode: String,
    onBack: () -> Unit, initialModule: String? = null, initialEntry: String? = null, downloads: Boolean = false, initialQuery: String = "",atlasOnly:Boolean=false) {
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf<List<ReferenceTarget>?>(null) }
    var code by remember { mutableStateOf(savedCode) }
    var failed by remember { mutableStateOf(false) }
    val openReference: (DictionaryReference) -> Unit = { reference ->
        scope.launch { try {
            val resolved=resolveStudyReference(client,savedCode,reference)
            code=resolved.code;targets=resolved.targets
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } }
    }
    if (downloads) StudyDownloadsScreen(language, onBack, onReference = openReference)
    else DictionariesScreen(language, onBack, initialModule, initialEntry, onReference = openReference, initialQuery = initialQuery,atlasOnly=atlasOnly)
    targets?.let { TemporaryStudyPassage(language, code, it, client) { targets = null } }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, text = { Text(studyTexts(language).missing) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(localized(R.string.study_close, language)) } })
}
