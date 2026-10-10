package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class ReaderToolAction(val id:String,val label:String,val icon:ImageVector,val action:()->Unit)

/** Observe the content edge; ordinary buttons and reading gestures receive untouched events. */
internal fun Modifier.readerToolbarTopEdge(onOpen:()->Unit)=pointerInput(onOpen){
    val edge=24.dp.toPx();val distance=56.dp.toPx();val horizontal=16.dp.toPx()
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        if(down.position.y !in 0f..edge)return@awaitEachGesture
        while(true){
            val event=awaitPointerEvent(PointerEventPass.Initial)
            if(event.changes.any{it.id!=down.id&&it.pressed})break
            val current=event.changes.firstOrNull{it.id==down.id}?:break
            if(!current.pressed)break
            val movement=current.position-down.position
            if(abs(movement.x)>horizontal||movement.y < -viewConfiguration.touchSlop)break
            if(movement.y>=distance){current.consume();onOpen();break}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun ReaderToolbar(expanded:Boolean,onExpanded:(Boolean)->Unit,actions:List<ReaderToolAction>,label:String,closeLabel:String){
    Box {
        ReaderToolIcon("open",label,Icons.Outlined.MoreHoriz,{onExpanded(!expanded)})
        DropdownMenu(expanded=expanded,onDismissRequest={onExpanded(false)},modifier=Modifier.width(248.dp).testTag("reader-tools-popup")){
            FlowRow(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=4){
                actions.forEach { tool ->ReaderToolIcon(tool.id,tool.label,tool.icon,{onExpanded(false);tool.action()}) }
                ReaderToolIcon("close",closeLabel,Icons.Outlined.Close,{onExpanded(false)})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ReaderToolIcon(id:String,label:String,icon:ImageVector,onClick:()->Unit){
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip{Text(label)}},state=rememberTooltipState()){
        IconButton(onClick=onClick,modifier=Modifier.testTag("reader-tool-$id")){
            Icon(icon,contentDescription=label)
        }
    }
}
