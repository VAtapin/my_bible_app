package com.bibledesktop.myapp.ui.study

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val passageReadSlots = Semaphore(2)

/** Read explicitly saved identities in the selected edition; this is not a mapping assertion. */
internal suspend fun resolveReferenceCardText(client: BibleContentSource, code: String, targets: List<ReferenceTarget>): Map<String,String> = passageReadSlots.withPermit {
    val result = linkedMapOf<String,String>()
    val refs = targets.map { it.osisRef }.distinct()
    // The location API accepts at most 200 identities. Chapters are fetched once per batch.
    val chapters = mutableMapOf<Pair<String,Int>,BibleChapter?>()
    for (batch in refs.chunked(200)) {
        val locations = try { client.getVerseLocations(code,batch) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { continue }
        for (location in locations.filter { it.osis in batch }.distinctBy { it.osis }) {
            if (locations.count { it.osis == location.osis } != 1) continue
            val key = location.book to location.chapter
            if (key !in chapters) chapters[key] = try {
                client.getChapter(code,location.book,location.chapter).also {
                    require(it.translation.code==code && it.book.slug==location.book && it.chapter.number==location.chapter)
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            val verse = chapters[key]?.verses?.singleOrNull {
                it.id==location.verseId && it.osisRef==location.osis && it.number==location.verse
            }
            verse?.plainText?.takeIf { it.isNotBlank() }?.let { result[location.osis]=it }
        }
    }
    result
}

internal fun referenceGroupAvailable(group:ReferenceGroup,actual:Map<String,String>):Boolean = group.targets.isNotEmpty() && group.targets.all { (actual[it.osisRef] ?: referencePreviewText(it,"" )).isNotBlank() }

@Composable internal fun ReferencePassageCards(language:String, chapter:BibleChapter, groups:List<ReferenceGroup>, client:BibleContentSource, onOpen:(ReferenceGroup)->Unit) {
    val identities = groups.flatMap { it.targets }.map { it.osisRef }.distinct()
    var actual by remember(client,chapter.translation.code,identities) { mutableStateOf(emptyMap<String,String>()) }
    LaunchedEffect(client,chapter.translation.code,identities) {
        actual=resolveReferenceCardText(client,chapter.translation.code,groups.flatMap { it.targets })
    }
    groups.forEach { ReferencePassageCard(language,chapter,it,actual) { onOpen(it) } }
}

@Composable internal fun ReferencePassageCard(language:String,chapter:BibleChapter,group:ReferenceGroup,actual:Map<String,String> = emptyMap(),onOpen:()->Unit) {
    if(!referenceGroupAvailable(group,actual))return
    val context=LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick=onOpen,modifier=Modifier.testTag("reference-label-${group.targets.first().osisRef}")) {
                Text(group.label,style=MaterialTheme.typography.titleMedium)
            }
            group.targets.forEach { target ->
                val body=actual[target.osisRef] ?: referencePreviewText(target,"")
                if(body.isNotBlank()) SelectionContainer {
                    Text("${target.verseNumber} $body",fontFamily=readingFont(chapter.translation.language.code),fontSize=18.sp,lineHeight=27.sp)
                }
            }
            Row {
                TextButton(onClick=onOpen,modifier=Modifier.testTag("reference-${group.targets.first().osisRef}")) { Text(localized(R.string.bookmark_open,language)) }
                TextButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(group.label,referenceCopyText(group,chapter.translation.name,actual))) }) { Text(referenceDisplayTexts(language).copy) }
            }
        }
    }
}
