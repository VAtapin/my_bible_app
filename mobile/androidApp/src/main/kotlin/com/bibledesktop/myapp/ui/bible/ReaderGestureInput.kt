package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Controls and consumed motion win; a short consumed tap may page only measured ordinary text. */
@Composable internal fun Modifier.readerGestures(settings: ReaderPreferences, selectionActive: () -> Boolean,
    onPage: (Int) -> Unit, onChapter: (Int) -> Unit, onBook: (Int) -> Unit,
    ordinaryTextAt: (Offset) -> Boolean = { false }): Modifier {
    val selectionNow by rememberUpdatedState(selectionActive)
    val pageNow by rememberUpdatedState(onPage)
    val chapterNow by rememberUpdatedState(onChapter)
    val bookNow by rememberUpdatedState(onBook)
    val ordinaryTextNow by rememberUpdatedState(ordinaryTextAt)
    return if (!settings.tapPaging && !settings.swipeChapters && !settings.swipeBooks) this else pointerInput(settings) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Final)
            val selectionStarted=selectionNow()
            val start=down.position; var end=start; var fingers=1; var consumed=false;var upConsumed=false;var ended=down.uptimeMillis
            do {
                val event=awaitPointerEvent(PointerEventPass.Final)
                ended=event.changes.maxOfOrNull{it.uptimeMillis} ?: ended
                fingers=maxOf(fingers,event.changes.count {it.pressed || it.previousPressed})
                event.changes.firstOrNull {it.id==down.id}?.let {end=it.position}
                consumed=consumed || event.changes.any {it.isConsumed && it.position!=it.previousPosition}
                if(event.changes.none{it.pressed})upConsumed=event.changes.any{it.isConsumed}
            } while(event.changes.any {it.pressed})
            if (!consumed && !selectionStarted && !selectionNow()) {
                val dx=end.x-start.x; val dy=end.y-start.y; val threshold=80 * density
                if (abs(dx)>threshold && abs(dx)>abs(dy)*2) {
                    if(fingers==2 && settings.swipeBooks) bookNow(if(dx<0) 1 else -1)
                    else if(fingers==1 && settings.swipeChapters) chapterNow(if(dx<0) 1 else -1)
                } else if((!upConsumed || ordinaryTextNow(start)) && fingers==1 && settings.tapPaging && abs(dx)<12*density && abs(dy)<12*density && (ended-down.uptimeMillis)<350) {
                    if(start.x<size.width*.25 || start.x>size.width*.75) pageNow(if(start.x<size.width*.25) -1 else 1)
                }
            }
        }
    }
}
