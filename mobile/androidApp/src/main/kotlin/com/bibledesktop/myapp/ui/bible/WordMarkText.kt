package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Horizontal motion starts marking; preceding vertical motion is left to the scroll container. */
@Composable internal fun WordMarkText(text: String, enabled: Boolean, selection: TextRange, onSelection: (TextRange) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selectedColor = MaterialTheme.colorScheme.primaryContainer
    val marked = remember(text, selection, selectedColor) { buildAnnotatedString { append(text); if (!selection.collapsed && selection.max <= text.length) addStyle(SpanStyle(background = selectedColor), selection.min, selection.max) } }
    val callback by rememberUpdatedState(onSelection)
    val content: @Composable () -> Unit = {
        BasicText(marked, Modifier.fillMaxWidth().padding(10.dp).then(if (enabled) Modifier.pointerInput(text, enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val first = layout?.getOffsetForPosition(down.position) ?: 0
                var dragging = false
                var cancelled = false
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - down.position
                    if (!dragging && !cancelled) {
                        if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) cancelled = true
                        else if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) dragging = true
                    }
                    if (dragging) {
                        change.consume()
                        val last = layout?.getOffsetForPosition(change.position) ?: first
                        callback(TextRange(first.coerceIn(0, text.length), last.coerceIn(0, text.length)))
                    }
                } while (change.pressed)
            }
        } else Modifier), style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), onTextLayout = { layout = it })
    }
    if (enabled) content() else SelectionContainer { content() }
}
