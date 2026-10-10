package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderSettingsDialog(language: String, value: ReaderPreferences, onChange: (ReaderPreferences) -> Unit, onClose: () -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val referenceStore=remember(context){com.bibledesktop.myapp.ui.study.ReferenceDisplayStore(context)}
    val referenceRevision by com.bibledesktop.myapp.ui.study.ReferenceDisplayStore.changes.collectAsState()
    val referenceSettings=remember(referenceRevision){referenceStore.load()}
    val referenceText=com.bibledesktop.myapp.ui.study.referenceDisplayTexts(language)
    fun text(key: String) = readerControlText(language,key)
    AlertDialog(onDismissRequest=onClose, title={ Text(text("settings")) }, confirmButton={ TextButton(onClick=onClose) { Text(text("close")) } },
        text={ Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState())) {
            Text("${text("fontSize")}: ${value.fontSize.toInt()}")
            Slider(value.fontSize,{onChange(value.copy(fontSize=it))},valueRange=14f..36f,steps=21)
            Text("${text("lineHeight")}: ${"%.2f".format(value.lineHeight)}")
            Slider(value.lineHeight,{onChange(value.copy(lineHeight=it))},valueRange=1.2f..2.2f)
            ReaderSwitch(text("chapterLabels"),value.chapterLabels) {onChange(value.copy(chapterLabels=it))}
            ReaderSwitch(text("verseNumbers"),value.verseNumbers) {onChange(value.copy(verseNumbers=it))}
            ReaderSwitch(text("separateVerses"),value.separateVerses) {onChange(value.copy(separateVerses=it))}
            ReaderSwitch(text("headings"),value.headings) {onChange(value.copy(headings=it))}
            ReaderSwitch(text("crossReferences"),value.crossReferences) {onChange(value.copy(crossReferences=it))}
            TextButton(onClick={referenceStore.save(referenceSettings.copy(list=!referenceSettings.list))}) {Text(if(referenceSettings.list)referenceText.list else referenceText.compact)}
            ReaderSwitch(text("commentaryLinks"),value.commentaryLinks) {onChange(value.copy(commentaryLinks=it))}
            ReaderSwitch(text("footnotes"),value.footnotes) {onChange(value.copy(footnotes=it))}
            ReaderSwitch(text("strongNumbers"),value.strongNumbers) {onChange(value.copy(strongNumbers=it))}
            ReaderSwitch(text("paragraphs"),value.paragraphs) {onChange(value.copy(paragraphs=it))}
            ReaderSwitch(text("addedWords"),value.addedWords) {onChange(value.copy(addedWords=it))}
            ReaderSwitch(text("clean"),value.clean) {onChange(value.copy(clean=it))}
            ReaderSwitch(text("night"),value.night) {onChange(value.copy(night=it))}
            ReaderSwitch(text("tapPaging"),value.tapPaging) {onChange(value.copy(tapPaging=it))}
            ReaderSwitch(text("swipeChapters"),value.swipeChapters) {onChange(value.copy(swipeChapters=it))}
            ReaderSwitch(text("swipeBooks"),value.swipeBooks) {onChange(value.copy(swipeBooks=it))}
            ReaderSwitch(text("volumePaging"),value.volumePaging) {onChange(value.copy(volumePaging=it))}
            Text(text("available"),style=MaterialTheme.typography.bodySmall)
        } })
}
@Composable
private fun ReaderSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f)); Switch(checked,onChange)
    }
}
@Composable
internal fun ReaderHistoryDialog(language: String, history: ReaderHistoryStore, onNavigate: (ReaderHistoryPlace) -> Unit, onClose: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    fun apply(place: ReaderHistoryPlace?) { revision++; place?.let(onNavigate) }
    AlertDialog(onDismissRequest=onClose, title={ Text(readerControlText(language,"history")) },
        confirmButton={ TextButton(onClick=onClose) {Text(readerControlText(language,"close"))} }, text={
            key(revision) { Column {
                Row {
                    TextButton(onClick={apply(history.back())},enabled=history.canBack) {Text(readerControlText(language,"back"))}
                    TextButton(onClick={apply(history.forward())},enabled=history.canForward) {Text(readerControlText(language,"forward"))}
                }
                LazyColumn(Modifier.heightIn(max=400.dp)) { itemsIndexed(history.entries) { index, place ->
                    TextButton(onClick={apply(history.select(index))}) { Text("${if(index==history.cursor) "• " else ""}${place.code} · ${place.book} ${place.chapter}:${place.verse.coerceAtLeast(1)}") }
                } }
            } }
        })
}
