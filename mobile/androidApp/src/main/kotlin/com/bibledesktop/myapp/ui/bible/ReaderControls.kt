package com.bibledesktop.myapp.ui.bible

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ReaderPreferences(
    val chapterLabels: Boolean = true, val verseNumbers: Boolean = true, val separateVerses: Boolean = true,
    val headings: Boolean = true, val crossReferences: Boolean = true, val commentaryLinks: Boolean = true,
    val footnotes: Boolean = true, val strongNumbers: Boolean = false, val paragraphs: Boolean = true,
    val addedWords: Boolean = true, val clean: Boolean = false, val night: Boolean = false,
    val fontSize: Float = 19f, val lineHeight: Float = 1.55f,
    val tapPaging: Boolean = false, val swipeChapters: Boolean = false, val swipeBooks: Boolean = false,
    val volumePaging: Boolean = false,
) {
    fun effective() = if (clean) copy(chapterLabels=false, verseNumbers=false, headings=false,
        crossReferences=false, commentaryLinks=false, footnotes=false, strongNumbers=false) else this
}
internal val LocalReaderPreferences = staticCompositionLocalOf { ReaderPreferences() }
internal data class ReaderNavigationActions(val chapter: (Int) -> Unit = {}, val book: (Int) -> Unit = {})
internal val LocalReaderNavigationActions = staticCompositionLocalOf { ReaderNavigationActions() }
internal class ReaderPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("bible-desktop-reader-controls", Context.MODE_PRIVATE)
    private val legacyFont=context.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE).getFloat("readerFontSize",19f)
    fun load(): ReaderPreferences = ReaderPreferences(
        chapterLabels=prefs.getBoolean("chapterLabels",true), verseNumbers=prefs.getBoolean("verseNumbers",true),
        separateVerses=prefs.getBoolean("separateVerses",true), headings=prefs.getBoolean("headings",true),
        crossReferences=prefs.getBoolean("crossReferences",true), commentaryLinks=prefs.getBoolean("commentaryLinks",true),
        footnotes=prefs.getBoolean("footnotes",true), strongNumbers=prefs.getBoolean("strongNumbers",false),
        paragraphs=prefs.getBoolean("paragraphs",true), addedWords=prefs.getBoolean("addedWords",true),
        clean=prefs.getBoolean("clean",false), night=prefs.getBoolean("night",false),
        fontSize=prefs.getFloat("fontSize",legacyFont).takeIf(Float::isFinite)?.coerceIn(14f,36f) ?: 19f,
        lineHeight=prefs.getFloat("lineHeight",1.55f).takeIf(Float::isFinite)?.coerceIn(1.2f,2.2f) ?: 1.55f,
        tapPaging=prefs.getBoolean("tapPaging",false), swipeChapters=prefs.getBoolean("swipeChapters",false),
        swipeBooks=prefs.getBoolean("swipeBooks",false), volumePaging=prefs.getBoolean("volumePaging",false),
    )
    fun save(p: ReaderPreferences) { prefs.edit().apply {
        putBoolean("chapterLabels",p.chapterLabels); putBoolean("verseNumbers",p.verseNumbers); putBoolean("separateVerses",p.separateVerses)
        putBoolean("headings",p.headings); putBoolean("crossReferences",p.crossReferences); putBoolean("commentaryLinks",p.commentaryLinks)
        putBoolean("footnotes",p.footnotes); putBoolean("strongNumbers",p.strongNumbers); putBoolean("paragraphs",p.paragraphs)
        putBoolean("addedWords",p.addedWords); putBoolean("clean",p.clean); putBoolean("night",p.night)
        putFloat("fontSize",p.fontSize.coerceIn(14f,36f)); putFloat("lineHeight",p.lineHeight.coerceIn(1.2f,2.2f))
        putBoolean("tapPaging",p.tapPaging); putBoolean("swipeChapters",p.swipeChapters); putBoolean("swipeBooks",p.swipeBooks)
        putBoolean("volumePaging",p.volumePaging); apply()
    } }
}
internal data class ReaderHistoryPlace(val code: String, val book: String, val chapter: Int, val verse: Int, val offset: Int = 0) {
    fun valid() = code.isNotBlank() && book.isNotBlank() && chapter > 0 && verse >= 0 && offset >= 0
    fun json() = JSONObject().put("code",code).put("book",book).put("chapter",chapter).put("verse",verse).put("offset",offset)
}
internal class ReaderHistoryStore(context: Context, private val windowKey: String) {
    private val prefs = context.getSharedPreferences("bible-desktop-reader-history", Context.MODE_PRIVATE)
    var entries = listOf<ReaderHistoryPlace>(); private set
    var cursor = -1; private set
    var current: ReaderHistoryPlace? = null; private set
    init { runCatching {
        val root = JSONObject(prefs.getString(windowKey,null) ?: "{}"); val list = root.getJSONArray("entries")
        require(list.length() <= 200)
        val parsed = (0 until list.length()).map { list.getJSONObject(it).let { p -> ReaderHistoryPlace(p.getString("code"),p.getString("book"),p.getInt("chapter"),p.getInt("verse"),p.getInt("offset")) } }
        val index = root.getInt("cursor"); require(parsed.all { it.valid() } && index in parsed.indices)
        entries=parsed; cursor=index; current=parsed[index]
    } }
    private fun save() { prefs.edit().putString(windowKey,JSONObject().put("entries",JSONArray(entries.map { it.json() })).put("cursor",cursor).toString()).apply() }
    fun observe(place: ReaderHistoryPlace) { if (!place.valid()) return; current=place; if (entries.isEmpty()) { entries=listOf(place); cursor=0; save() } }
    fun navigate(target: ReaderHistoryPlace) {
        if (!target.valid()) return
        val list = entries.take(cursor+1).toMutableList()
        current?.let { if (list.lastOrNull() != it) list.add(it) }
        if (list.lastOrNull() != target) list.add(target)
        entries=list.takeLast(200); cursor=entries.lastIndex; current=target; save()
    }
    val canBack get() = cursor > 0 || (current != null && current != entries.getOrNull(cursor))
    val canForward get() = cursor+1 < entries.size
    fun back(): ReaderHistoryPlace? {
        val anchor = entries.getOrNull(cursor)
        if (anchor != null && current != null && current != anchor) {
            val list=entries.toMutableList().apply { add(cursor+1,current!!) }
            if(list.size>200) {if(cursor==0)list.removeAt(list.lastIndex) else {list.removeAt(0);cursor--}}
            entries=list; current=anchor; save(); return anchor
        }
        return select(cursor-1)
    }
    fun forward() = select(cursor+1)
    fun select(index: Int): ReaderHistoryPlace? { if (index !in entries.indices) return null; cursor=index; current=entries[index]; save(); return current }
}
/** Activity dispatches only while a mounted reader explicitly registers its handler. */
object ReaderVolumeKeys {
    var handler: ((Int) -> Unit)? = null
    fun dispatch(event: KeyEvent): Boolean {
        val direction = when(event.keyCode) { KeyEvent.KEYCODE_VOLUME_DOWN -> 1; KeyEvent.KEYCODE_VOLUME_UP -> -1; else -> return false }
        val action = handler ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && !event.isCanceled) action(direction)
        return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
    }
}
@Composable
internal fun ReaderVolumePaging(enabled: Boolean, onPage: (Int) -> Unit) {
    val currentPage by rememberUpdatedState(onPage)
    DisposableEffect(enabled) {
        val handler: (Int) -> Unit = { currentPage(it) }
        if (enabled) ReaderVolumeKeys.handler=handler
        onDispose { if (ReaderVolumeKeys.handler === handler) ReaderVolumeKeys.handler=null }
    }
}
@Composable
internal fun rememberReaderPreferences(): Pair<ReaderPreferences, (ReaderPreferences) -> Unit> {
    val context=LocalContext.current; val store=remember(context) { ReaderPreferencesStore(context) }
    var value by remember(store) { mutableStateOf(store.load()) }
    return value to { value=it; store.save(it) }
}
