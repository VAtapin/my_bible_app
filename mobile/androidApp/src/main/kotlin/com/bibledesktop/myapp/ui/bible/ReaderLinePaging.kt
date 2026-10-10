package com.bibledesktop.myapp.ui.bible

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy

internal data class ReadingLine(val top:Float,val bottom:Float)
internal fun measuredPageDistance(lines:List<ReadingLine>,top:Float,bottom:Float,direction:Int):Float? {
    val ordered=lines.filter{it.top.isFinite()&&it.bottom>it.top}.sortedBy{it.top}
    val boundary=if(direction>0)bottom else top-(bottom-top)
    val target=ordered.lastOrNull{it.top<boundary&&it.bottom>boundary} ?: ordered.firstOrNull{it.top>=boundary} ?: return null
    if(direction>0&&target.top<=top||direction<0&&target.top>=top)return null
    return target.top-top
}
internal fun firstReadingLineDistance(lines:List<ReadingLine>,top:Float,bottom:Float):Float =
    lines.filter{it.bottom>top&&it.top<bottom}.minByOrNull{it.top}?.let{it.top-top} ?: 0f

/** Each viewport owns measurements; parallel panes never share scroll coordinates. */
internal class ReaderLineMeasurements {
    private data class TextLines(var layout:TextLayoutResult?=null,var coordinates:LayoutCoordinates?=null)
    private val texts=mutableMapOf<Any,TextLines>()
    var viewport:LayoutCoordinates?=null
    fun layout(key:Any,value:TextLayoutResult){texts.getOrPut(key){TextLines()}.layout=value}
    fun coordinates(key:Any,value:LayoutCoordinates){texts.getOrPut(key){TextLines()}.coordinates=value}
    fun remove(key:Any){texts.remove(key)}
    fun bounds():Rect?=viewport?.takeIf{it.isAttached}?.boundsInRoot()
    fun lines():List<ReadingLine> = texts.values.flatMap { text ->
        val layout=text.layout
        val origin=text.coordinates?.takeIf{it.isAttached}?.positionInRoot()
        if(layout==null||origin==null)emptyList() else (0 until layout.lineCount).map { line ->
            ReadingLine(origin.y+layout.getLineTop(line),origin.y+layout.getLineBottom(line))
        }
    }
    fun page(direction:Int):Float?=bounds()?.let{measuredPageDistance(lines(),it.top,it.bottom,direction)}
    fun alignFirst():Float=bounds()?.let{firstReadingLineDistance(lines(),it.top,it.bottom)} ?: 0f
    fun ordinaryTextAt(point:Offset):Boolean {
        val root=viewport?.takeIf{it.isAttached}?.positionInRoot()?.plus(point) ?: return false
        return texts.values.any { text ->
            val coordinates=text.coordinates?.takeIf{it.isAttached} ?: return@any false
            val layout=text.layout ?: return@any false
            val local=root-coordinates.positionInRoot()
            if(local.x<0||local.y<0||local.x>=coordinates.size.width||local.y>=coordinates.size.height)return@any false
            val offset=layout.getOffsetForPosition(local)
            listOf("source-footnote","source-strong","word-note").none { tag ->
                layout.layoutInput.text.getStringAnnotations(tag,offset,offset).isNotEmpty()
            }
        }
    }
}
internal val LocalReaderLineMeasurements=staticCompositionLocalOf<ReaderLineMeasurements?>{null}
internal suspend fun pageMeasuredReader(state:LazyListState,measurements:ReaderLineMeasurements,direction:Int) {
    val distance=measurements.page(direction)
    if(distance!=null)state.animateScrollBy(distance)
    else {
        val height=measurements.bounds()?.height ?: return
        state.scrollBy(direction*height)
        // Lazy rows from the preceding page may only obtain text layout after scrolling.
        withFrameNanos{}
        state.scrollBy(measurements.alignFirst())
    }
}
@Composable internal fun measuredReadingText():Pair<Modifier,(TextLayoutResult)->Unit> {
    val measurements=LocalReaderLineMeasurements.current
    val key=remember{Any()}
    DisposableEffect(measurements,key){onDispose{measurements?.remove(key)}}
    return Modifier.onGloballyPositioned{measurements?.coordinates(key,it)} to {value:TextLayoutResult->measurements?.layout(key,value);Unit}
}
