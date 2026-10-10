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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.layout.onGloballyPositioned

private data class InterleavedPreviewSnapshot(val chapter:BibleChapter,val code:String,val verse:Int,val offset:Int)

@Composable
internal fun ComparisonPane(language: String, primary: BibleChapter, catalog: List<TranslationSummary>,
    code: String, onCode: (String) -> Unit, source: BibleContentSource, fontSize: Float, initialVerse: Int, modifier: Modifier = Modifier,
    bookmarks: Set<String> = emptySet(), onBookmark: (BibleChapter, BibleVerse) -> Unit = { _, _ -> },
    onShare: (BibleChapter, BibleVerse) -> Unit = { _, _ -> }, onNote: (BibleChapter, BibleVerse) -> Unit = { _, _ -> },
    onStudy: ((BibleChapter, BibleVerse) -> Unit)? = null, onVisible: (BibleChapter, BibleVerse, BibleVerse, Int) -> Unit = { _, _, _, _ -> },
    onCommands: (WindowCommands?) -> Unit = {},
    onPersonal: ((BibleChapter,BibleVerse)->Unit)? = null, selection: SavedPassage? = null,
    onStrong: ((BibleChapter,BibleVerse,String)->Unit)? = null) {
    val preferences = LocalContext.current.getSharedPreferences("bible-desktop-native-profile", android.content.Context.MODE_PRIVATE)
    var paneCommands by remember {mutableStateOf<WindowCommands?>(null)}
    var previewActive by remember {mutableStateOf(false)}
    var previewPending by remember {mutableStateOf<Triple<BibleChapter,Int,Int>?>(null)}
    var interleavedSnapshot by remember {mutableStateOf<InterleavedPreviewSnapshot?>(null)}
    var panes by remember { mutableStateOf(preferences.getBoolean("comparePanes", false)) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var rowSource by remember(primary) { mutableStateOf(primary) }
    var rowCode by remember(code) { mutableStateOf(code) }
    var rowVerse by remember(initialVerse) { mutableIntStateOf(initialVerse) }
    var rowOffset by remember {mutableIntStateOf(0)}
    var rowCurrent by remember(primary) {mutableStateOf(primary)}
    var currentVerse by remember(initialVerse) {mutableIntStateOf(initialVerse)}
    var currentOffset by remember {mutableIntStateOf(0)}
    var counterpart by remember {mutableStateOf<BibleChapter?>(null)}
    val context=LocalContext.current
    val windowErrorText=localized(R.string.windows_error,language)
    val windowMissingText=localized(R.string.windows_missing,language)
    val windowPreferences=remember {context.getSharedPreferences("bible-desktop-reader-windows",android.content.Context.MODE_PRIVATE)}
    var activeId by remember {mutableIntStateOf(windowPreferences.getInt("active",0).coerceIn(0,1))}
    val history=remember(activeId) {ReaderHistoryStore(context,"bible-desktop-reader-windows:$activeId")}
    var historyOpen by remember {mutableStateOf(false)}
    var passagePicker by remember {mutableStateOf<Boolean?>(null)}
    val scope=rememberCoroutineScope()
    suspend fun navigate(place:ReaderHistoryPlace,record:Boolean=true){
        try{
            val value=source.getChapter(place.code,place.book,place.chapter)
            require(value.translation.code==place.code&&value.book.slug==place.book&&value.chapter.number==place.chapter)
            require(place.verse==0||value.verses.any{it.number==place.verse&&it.plainText.isNotBlank()})
            if(record)history.navigate(place)
            rowSource=value;rowCurrent=value;rowVerse=place.verse;currentVerse=place.verse;rowOffset=place.offset;currentOffset=place.offset
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){android.widget.Toast.makeText(context,windowErrorText,android.widget.Toast.LENGTH_LONG).show()}
    }
    fun observed(value:BibleChapter,first:BibleVerse,last:BibleVerse,offset:Int){
        rowCurrent=value;currentVerse=first.number;currentOffset=offset
        if(!panes)history.observe(ReaderHistoryPlace(value.translation.code,value.book.slug,value.chapter.number,first.number,offset))
        var array=runCatching{org.json.JSONArray(windowPreferences.getString("places","[]"))}.getOrDefault(org.json.JSONArray())
        if(array.length()!=2){
            val other=counterpart ?: if(rowCode==value.translation.code)value else null
            if(other==null){onVisible(value,first,last,offset);return}
            array=org.json.JSONArray()
            listOf(value,other).forEach{item->array.put(org.json.JSONObject().put("code",item.translation.code).put("book",item.book.slug).put("chapter",item.chapter.number).put("verse",item.verses.firstOrNull()?.number?:0).put("offset",0))}
        }
        array.put(activeId,org.json.JSONObject().put("code",value.translation.code).put("book",value.book.slug).put("chapter",value.chapter.number).put("verse",first.number).put("offset",offset))
        if(!panes)windowPreferences.edit().putString("places",array.toString()).apply()
        onVisible(value,first,last,offset)
    }
    val chooseAction by rememberUpdatedState<(Boolean)->Unit>({passagePicker=it})
    val moveAction by rememberUpdatedState<(Int)->Unit>({delta->val ch=rowCurrent.chapter.number+delta;if(ch in 1..rowCurrent.book.chaptersCount)scope.launch{navigate(ReaderHistoryPlace(rowCurrent.translation.code,rowCurrent.book.slug,ch,1))}})
    val backAction by rememberUpdatedState<()->Unit>({history.back()?.let{scope.launch{navigate(it,false)}}})
    val forwardAction by rememberUpdatedState<()->Unit>({history.forward()?.let{scope.launch{navigate(it,false)}}})
    val translateAction by rememberUpdatedState<(String)->Unit>({other->scope.launch{try{val ref=rowCurrent.verses.firstOrNull{it.number==currentVerse}?.osisRef ?: throw ComparisonUnavailable();val location=source.getVerseLocations(other,listOf(ref)).firstOrNull{it.osis==ref} ?: throw ComparisonUnavailable();val value=source.getChapter(other,location.book,location.chapter);val verse=value.verses.firstOrNull{it.osisRef==ref&&it.id==location.verseId&&it.number==location.verse&&it.plainText.isNotBlank()}?:throw ComparisonUnavailable();navigate(ReaderHistoryPlace(value.translation.code,value.book.slug,value.chapter.number,verse.number))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){android.widget.Toast.makeText(context,windowMissingText,android.widget.Toast.LENGTH_LONG).show()}}})
    val previewAction by rememberUpdatedState<(BibleChapter,Int,Int)->Unit>({value,verse,id->
        if(interleavedSnapshot==null)interleavedSnapshot=InterleavedPreviewSnapshot(rowCurrent,rowCode,currentVerse,currentOffset)
        rowSource=rowCurrent;rowVerse=currentVerse;rowOffset=currentOffset;previewActive=true;previewPending=Triple(value,verse,id);panes=true
    })
    fun previewReturned(){previewActive=false;interleavedSnapshot?.let{saved->rowSource=saved.chapter;rowCurrent=saved.chapter;rowCode=saved.code;rowVerse=saved.verse;currentVerse=saved.verse;rowOffset=saved.offset;currentOffset=saved.offset;interleavedSnapshot=null;panes=false}}
    val sourceAction by rememberUpdatedState<()->BibleChapter?>({rowCurrent})
    val rowCommands=remember {WindowCommands({chooseAction(it)},{moveAction(it)},{historyOpen=true},{backAction()},{forwardAction()},{translateAction(it)},preview={c,v,id->previewAction(c,v,id)},source={sourceAction()})}
    val forwardedPaneCommands=remember(paneCommands){paneCommands?.let{actual->actual.copy(preview={c,v,id->previewActive=true;actual.preview(c,v,id)})}}
    LaunchedEffect(panes,paneCommands,previewPending){val pending=previewPending;val actual=paneCommands;if(panes&&actual!=null&&pending!=null){previewPending=null;actual.preview(pending.first,pending.second,pending.third)}}
    if(panes){SideEffect{onCommands(forwardedPaneCommands)};DisposableEffect(Unit){onDispose{onCommands(null)}}}
    if(!panes){SideEffect{onCommands(rowCommands)};DisposableEffect(Unit){onDispose{onCommands(null)}}}
    var second by remember(primary.translation.code, primary.book.slug, primary.chapter.number, code) { mutableStateOf<List<BibleChapter>?>(null) }
    var failure by remember(primary.translation.code, primary.book.slug, primary.chapter.number, code) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(rowSource, rowCode, retry, panes) {
        second = null; failure = 0
        if (panes) return@LaunchedEffect
        if (rowCode.isBlank()) return@LaunchedEffect
        try { second = if (rowCode == rowSource.translation.code) listOf(rowSource) else loadComparisonChapters(rowSource, rowCode, source);counterpart=second?.firstOrNull() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: ComparisonUnavailable) { failure = R.string.compare_unavailable }
        catch (_: Exception) { failure = R.string.compare_error }
    }
    Column(modifier.testTag("bible-comparison")) {
        if (!panes) TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("compare-translation")) {
            Text(catalog.firstOrNull { it.code == rowCode }?.name ?: localized(R.string.compare_choose, language))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(enabled=!previewActive,selected = !panes, onClick = { panes = false; preferences.edit().putBoolean("comparePanes", false).apply() },
                label = { Text(localized(R.string.compare_interleaved, language)) }, modifier = Modifier.weight(1f).testTag("compare-interleaved"))
            FilterChip(enabled=!previewActive,selected = panes, onClick = {rowSource=rowCurrent;rowVerse=currentVerse;rowOffset=currentOffset; panes = true; preferences.edit().putBoolean("comparePanes", true).apply() },
                label = { Text(localized(R.string.compare_panes, language)) }, modifier = Modifier.weight(1f).testTag("compare-panes"))
        }
        if (panes) ReaderWindows(language, rowSource, code, catalog, source, fontSize, rowVerse, Modifier.weight(1f),
            bookmarks, onBookmark, onShare, onNote, onStudy, ::observed, {paneCommands=it},
            onPair = { chapter, otherCode, verse -> activeId=windowPreferences.getInt("active",0).coerceIn(0,1);rowSource = chapter; rowCode = otherCode; rowVerse = verse;rowOffset=currentOffset },
            onPersonal=onPersonal,selection=selection,onStrong=onStrong,onPreviewReturned=::previewReturned)
        else when {
            rowCode.isBlank() -> Text(localized(R.string.compare_choose, language), Modifier.padding(16.dp))
            failure != 0 -> Column(Modifier.padding(16.dp)) {
                Text(localized(failure, language))
                TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
            }
            second == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            else -> key(rowSource.translation.code,rowSource.book.slug,rowSource.chapter.number,rowCode,rowVerse){
                CompositionLocalProvider(LocalReaderNavigationActions provides ReaderNavigationActions(
                    chapter={moveAction(it)},book={delta->scope.launch{try{val books=source.getBooks(rowCurrent.translation.code).sortedBy{it.order};books.getOrNull(books.indexOfFirst{it.slug==rowCurrent.book.slug}+delta)?.let{navigate(ReaderHistoryPlace(rowCurrent.translation.code,it.slug,1,1))}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){android.widget.Toast.makeText(context,windowErrorText,android.widget.Toast.LENGTH_LONG).show()}}}
                )){ComparisonRows(language,rowSource,second!!.first(),fontSize,Modifier.weight(1f),rowVerse,source,rowOffset,::observed,bookmarks,onBookmark,onShare,onNote,onStudy,onPersonal,selection,onStrong,secondaryChapters=second!!)}
            }
        }
    }
    if (choosing) TranslationPicker(language, catalog.filter { it.code != rowSource.translation.code }, rowCode,
        onSelect = { onCode(it); choosing = false }, onClose = { choosing = false })
    passagePicker?.let{books->PassagePicker(language,catalog,source,rowCurrent.translation.code,rowCurrent.book,rowCurrent.chapter.number,books,
        {code,book,ch->passagePicker=null;scope.launch{navigate(ReaderHistoryPlace(code,book.slug,ch,1))}},{passagePicker=null},{passagePicker=null},
        onVerseSelect={code,book,ch,verse->passagePicker=null;scope.launch{navigate(ReaderHistoryPlace(code,book.slug,ch,verse))}})}
    if(historyOpen)ReaderHistoryDialog(language,history,{scope.launch{navigate(it,false)}}){historyOpen=false}
}

@Composable
internal fun ComparisonRows(language: String, primary: BibleChapter, secondary: BibleChapter, fontSize: Float,
    modifier: Modifier = Modifier, initialVerse: Int = 0, source: BibleContentSource? = null, initialOffset: Int = 0,
    onVisible: ((BibleChapter,BibleVerse,BibleVerse,Int)->Unit)? = null,
    bookmarks: Set<String> = emptySet(), onBookmark: (BibleChapter,BibleVerse)->Unit = {_,_->},
    onShare: (BibleChapter,BibleVerse)->Unit = {_,_->}, onNote: (BibleChapter,BibleVerse)->Unit = {_,_->},
    onStudy: ((BibleChapter,BibleVerse)->Unit)? = null,onPersonal: ((BibleChapter,BibleVerse)->Unit)? = null,
    selection: SavedPassage? = null,onStrong: ((BibleChapter,BibleVerse,String)->Unit)? = null,secondaryChapters:List<BibleChapter> = listOf(secondary)) {
    val sections=remember(primary.translation.code,secondary.translation.code,primary.book.slug,primary.chapter.number) {mutableStateMapOf(primary.chapter.number to ComparisonChapterFrame(primary,secondary,secondaryChapters))}
    val rows=comparisonFrameRows(sections.toSortedMap().values.toList())
    val state = rememberLazyListState()
    var loading by remember {mutableStateOf(false)};var failed by remember {mutableStateOf(false)};var retry by remember {mutableIntStateOf(0)}
    val display=LocalReaderPreferences.current;val actions=LocalReaderNavigationActions.current;val context=LocalContext.current;val scope=rememberCoroutineScope()
    val lineMeasurements=remember{ReaderLineMeasurements()}
    val textToolbar=androidx.compose.ui.platform.LocalTextToolbar.current
    fun page(direction:Int){scope.launch{pageMeasuredReader(state,lineMeasurements,direction)}}
    if(source!=null)ReaderVolumePaging(display.volumePaging,::page)
    LaunchedEffect(primary.translation.code, secondary.translation.code, primary.book.slug, primary.chapter.number, initialVerse) {
        state.scrollToItem(rows.indexOfFirst { it.row.primary?.number == initialVerse&&it.frame.primary.chapter.number==primary.chapter.number }.coerceAtLeast(0),initialOffset.coerceAtLeast(0))
    }
    val first=sections.keys.min();val last=sections.keys.max()
    val nearEnd by remember {derivedStateOf{state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let{it>=state.layoutInfo.totalItemsCount-4}==true}}
    val nearStart by remember {derivedStateOf{state.firstVisibleItemIndex<=2&&state.layoutInfo.visibleItemsInfo.isNotEmpty()}}
    suspend fun load(number:Int,prepend:Boolean){
        if(source==null||loading||failed||number in sections||number !in 1..primary.book.chaptersCount)return
        loading=true
        try{
            val a=source.getChapter(primary.translation.code,primary.book.slug,number)
            validateComparedContinuation(primary,a,number)
            val bs=if(primary.translation.code==secondary.translation.code)listOf(a) else try{loadComparisonChapters(a,secondary.translation.code,source)}catch(_:ComparisonUnavailable){emptyList()}
            val b=bs.firstOrNull()
            val frame=ComparisonChapterFrame(a,b,bs);val index=state.firstVisibleItemIndex;val anchorRef=rows.getOrNull(index)?.row?.reference;val offset=state.firstVisibleItemScrollOffset
            sections[number]=frame
            if(prepend){val newRows=comparisonFrameRows(sections.toSortedMap().values.toList());val newIndex=newRows.indexOfFirst{it.row.reference==anchorRef};state.requestScrollToItem(if(newIndex>=0)newIndex else index,offset)}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}finally{loading=false}
    }
    LaunchedEffect(nearEnd,last,first,retry){if(nearEnd)load(last+1,false)}
    LaunchedEffect(nearStart,nearEnd,first,last,retry){if(nearStart&&!nearEnd)load(first-1,true)}
    LaunchedEffect(state,sections.toMap()){
        val byKey=rows.associateBy{it.row.reference}
        snapshotFlow{val layout=state.layoutInfo;layout.visibleItemsInfo.filter{it.offset+it.size>0&&it.offset<layout.viewportEndOffset}.map{it.key to it.offset}}.collectLatest{items->
            delay(180)
            val visible=items.mapNotNull{(key,offset)->byKey[key]?.takeIf{it.row.primary!=null}?.let{it to offset}}
            val top=visible.firstOrNull() ?: return@collectLatest
            val bottom=visible.lastOrNull{it.first.frame.primary.chapter.number==top.first.frame.primary.chapter.number} ?: top
            onVisible?.invoke(top.first.frame.primary,top.first.row.primary!!,bottom.first.row.primary!!,(-top.second).coerceAtLeast(0))
        }
    }
    CompositionLocalProvider(LocalReaderLineMeasurements provides lineMeasurements) {Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize()){
        if(failed)Row{Text(localized(R.string.reader_continuation_error,language),Modifier.weight(1f));TextButton(onClick={failed=false;retry++}){Text(localized(R.string.retry,language))}}
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("comparison-rows").onGloballyPositioned{lineMeasurements.viewport=it}.readerGestures(display,{selection!=null||textToolbar.status==androidx.compose.ui.platform.TextToolbarStatus.Shown},::page,actions.chapter,actions.book,lineMeasurements::ordinaryTextAt), state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = {it.row.reference}) { item ->
                val row=item.row;val chapter=item.frame.primary;val other=row.secondaryChapter
                val mainVerse=row.primary;val otherVerse=row.secondary
                val highlighted = initialVerse > 0 && row.primary?.number == initialVerse&&chapter.chapter.number==primary.chapter.number
                Card(Modifier.fillMaxWidth().testTag("compared-${row.reference}").semantics { selected = highlighted },
                    colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val reference = row.primary?.let { "${chapter.book.name} ${chapter.chapter.number}:${it.number}" }
                            ?: row.secondary!!.let { "${other!!.book.name} ${other.chapter.number}:${it.number}" }
                        if(!display.clean&&(display.chapterLabels||display.verseNumbers))Text(reference, style = MaterialTheme.typography.labelMedium)
                        if(source!=null&&mainVerse!=null) {Text(chapter.translation.name,style=MaterialTheme.typography.labelMedium);VerseRow(language,chapter,mainVerse,fontSize,"${chapter.translation.code}:${row.reference}" in bookmarks,{onBookmark(chapter,mainVerse)},{onShare(chapter,mainVerse)},{onNote(chapter,mainVerse)},onStudy=onStudy?.let{{it(chapter,mainVerse)}},onPersonal=onPersonal?.let{{it(chapter,mainVerse)}},selection=selection,onStrong=onStrong?.let{{number->it(chapter,mainVerse,number)}})}
                        else ComparedText(language, chapter.translation, row.primary, fontSize)
                        HorizontalDivider()
                        if(source!=null&&other!=null&&otherVerse!=null) {Text(other.translation.name,style=MaterialTheme.typography.labelMedium);VerseRow(language,other,otherVerse,fontSize,"${other.translation.code}:${row.reference}" in bookmarks,{onBookmark(other,otherVerse)},{onShare(other,otherVerse)},{onNote(other,otherVerse)},onStudy=onStudy?.let{{it(other,otherVerse)}},onPersonal=onPersonal?.let{{it(other,otherVerse)}},selection=selection,onStrong=onStrong?.let{{number->it(other,otherVerse,number)}})}
                        else ComparedText(language, other?.translation?:secondary.translation, row.secondary, fontSize)
                    }
                }
            }
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }}
}
internal fun validateComparedContinuation(initial:BibleChapter,value:BibleChapter,number:Int){
    require(value.translation.code==initial.translation.code&&value.book.slug==initial.book.slug&&value.chapter.number==number)
    val osis=initial.verses.firstOrNull()?.osisRef?.substringBefore('.') ?: value.verses.firstOrNull()?.osisRef?.substringBefore('.')
    require(value.verses.all{Regex("^${Regex.escape(osis.orEmpty())}\\.[1-9]\\d*\\.${it.number}$").matches(it.osisRef)}&&value.verses.map{it.osisRef}.distinct().size==value.verses.size)
}

@Composable
private fun ComparedText(language: String, translation: TranslationSummary, verse: BibleVerse?, size: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(translation.name, style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(verse?.let { if (translation.language.code in setOf("cu", "cu-civil")) readingText(it.text) else it.plainText }
                ?.takeIf { it.isNotBlank() } ?: localized(R.string.compare_missing, language),
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
                                        ?.takeIf { it.isNotBlank() } ?: localized(R.string.compare_missing, language), fontFamily = readingFont(chapter.translation.language.code),
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
