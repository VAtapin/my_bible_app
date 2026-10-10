package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Selection is staged: dismissing never changes the currently open passage. */
@Composable
internal fun PassagePicker(language: String, translations: List<TranslationSummary>, client: BibleContentSource,
    translationCode: String, book: BibleBook?, chapter: Int?, startWithBooks: Boolean,
    onSelect: (String, BibleBook, Int) -> Unit, onClose: () -> Unit, onHome: () -> Unit,
    initialBookSlug: String? = book?.slug,
    onVerseSelect: ((String, BibleBook, Int, Int) -> Unit)? = null) {
    var code by rememberSaveable { mutableStateOf(translationCode) }
    // On recreation the catalogue may still be loading; the reader's saved identity is already available.
    var slug by rememberSaveable { mutableStateOf(if (startWithBooks) null else initialBookSlug) }
    var books by remember(code) { mutableStateOf<List<BibleBook>?>(null) }
    var error by remember(code) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var verseChapter by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(code, retry) {
        books = null; error = false
        if (code.isBlank()) return@LaunchedEffect
        try { books = client.getBooks(code).sortedBy(BibleBook::order) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    val selectedBook = books?.firstOrNull { it.slug == slug }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 1000.dp).fillMaxSize(), color = Cream) {
            if (selectedBook == null) BookPicker(language, translations, code, books, error,
                onTranslationChange = { code = it; slug = null }, onBookClick = { slug = it.slug },
                onRetry = { retry++ }, onBack = onClose, onHome = onHome)
            else if (verseChapter != null && onVerseSelect != null) VersePassageScreen(language,code,selectedBook,verseChapter!!,client,
                onSelect={ch,verse -> onVerseSelect(code,selectedBook,ch,verse)},onClose=onClose,onBack={verseChapter=null})
            else ChaptersScreen(language, selectedBook, onChapterClick = { if(onVerseSelect == null) onSelect(code, selectedBook, it) else verseChapter=it },
                onBack = onClose, onHome = onHome,
                textLanguage = translations.firstOrNull { it.code == code }?.language?.code.orEmpty(),
                currentChapter = chapter.takeIf { code == translationCode && selectedBook.slug == book?.slug },
                onChooseBook = { slug = null })
        }
    }
}

@Composable
private fun VersePassageScreen(language: String, code: String, book: BibleBook, initialChapter: Int,
    client: BibleContentSource, onSelect: (Int,Int) -> Unit, onClose: () -> Unit, onBack: () -> Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("bible-desktop-reader-controls",android.content.Context.MODE_PRIVATE) }
    var digital by rememberSaveable {mutableStateOf(prefs.getBoolean("digitalNavigation",false))}
    var position by rememberSaveable {mutableStateOf("$initialChapter:1")}
    var selectedChapter by rememberSaveable {mutableIntStateOf(initialChapter)}
    var loaded by remember(code,book.slug) {mutableStateOf<BibleChapter?>(null)}
    var error by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var waiting by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(position,digital) {
        if(digital) position.trim().substringBefore(':').toIntOrNull()?.takeIf {it in 1..book.chaptersCount}?.let {selectedChapter=it}
    }
    LaunchedEffect(code,book.slug,selectedChapter,retry) {
        loaded=null; error=false
        try {
            val value=client.getChapter(code,book.slug,selectedChapter)
            require(value.translation.code==code && value.book.slug==book.slug && value.chapter.number==selectedChapter)
            loaded=value
        } catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {error=true}
    }
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader("${book.name} $selectedChapter",language,onBack,onClose)
        Row(Modifier.padding(horizontal=16.dp)) {
            TextButton(onClick={digital=!digital;prefs.edit().putBoolean("digitalNavigation",digital).apply()}) {Text(readerControlText(language,if(digital) "visual" else "digital"))}
            TextButton(onClick={onSelect(1,0)}) {Text("${readerControlText(language,"beginning")} · ${book.name}")}
            TextButton(onClick={onSelect(selectedChapter,0)}) {Text("${readerControlText(language,"beginning")} · $selectedChapter")}
        }
        Text("${localized(R.string.bible_chapters_count,language,book.chaptersCount)} · ${loaded?.verses?.size ?: "…"}",Modifier.padding(16.dp))
        if(digital) {
            OutlinedTextField(position,{position=it;error=false},singleLine=true,label={Text(readerControlText(language,"digital"))},modifier=Modifier.padding(16.dp).testTag("picker-digital-position"))
            Button(onClick={
                val match=Regex("^\\s*(\\d+)\\s*(?::\\s*(\\d+)\\s*)?$").matchEntire(position)
                val ch=match?.groupValues?.get(1)?.toIntOrNull(); val verse=match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                if(ch==null || ch !in 1..book.chaptersCount || verse<1) error=true
                else scope.launch {
                    waiting=true;error=false
                    try {
                        val value=if(loaded?.chapter?.number==ch) loaded!! else client.getChapter(code,book.slug,ch)
                        require(value.translation.code==code && value.book.slug==book.slug && value.chapter.number==ch && value.verses.any{it.number==verse && it.plainText.isNotBlank()})
                        onSelect(ch,verse)
                    } catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {error=true} finally{waiting=false}
                }
            },enabled=!waiting,modifier=Modifier.padding(horizontal=16.dp).testTag("picker-digital-go")) {Text(readerControlText(language,"go"))}
        } else if(loaded!=null) {
            LazyVerticalGrid(GridCells.Adaptive(58.dp),Modifier.weight(1f).testTag("verse-grid"),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(loaded!!.verses.filter{it.plainText.isNotBlank()},key={it.number}) {verse ->
                    OutlinedButton(onClick={onSelect(selectedChapter,verse.number)},modifier=Modifier.size(58.dp).testTag("choose-verse-${verse.number}"),contentPadding=PaddingValues(0.dp)) {Text(verse.number.toString())}
                }
            }
        } else if(!error) CircularProgressIndicator(Modifier.padding(16.dp))
        if(error) {Text(readerControlText(language,"invalid"),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}) {Text(localized(R.string.retry,language))}}
    }
}

@Composable
internal fun ChaptersScreen(language: String, book: BibleBook, onChapterClick: (Int) -> Unit,
    onBack: () -> Unit, onHome: () -> Unit, textLanguage: String = "",
    currentChapter: Int? = null, onChooseBook: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(book.name, language, onBack, onHome, readingFont(textLanguage))
        onChooseBook?.let { action ->
            TextButton(onClick = action, modifier = Modifier.padding(horizontal = 12.dp).testTag("picker-choose-book")) {
                Text(localized(R.string.bible_choose_book, language))
            }
        }
        Text(localized(R.string.bible_chapters, language), Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            color = Ink, fontFamily = ReadingSerif, fontSize = 24.sp)
        LazyVerticalGrid(columns = GridCells.Adaptive(58.dp), modifier = Modifier.weight(1f).testTag("chapter-grid"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..book.chaptersCount).toList()) { number ->
                Surface(Modifier.size(58.dp).testTag("choose-chapter-$number").clickable { onChapterClick(number) },
                    shape = RoundedCornerShape(16.dp), color = if (number == currentChapter) LightBlue else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (number == currentChapter) Navy else WarmBorder)) {
                    Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = Navy) }
                }
            }
        }
    }
}
