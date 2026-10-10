package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable internal fun AtlasImage(model: Any, title: String, language: String, onRetry:(()->Unit)?=null) {
    val text = dictionaryTexts(language)
    var fullscreen by remember { mutableStateOf(false) }
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var retry by remember(model) { mutableIntStateOf(0) }
    var state by remember(model,retry) { mutableIntStateOf(0) }
    val context=LocalContext.current
    val request=remember(model,retry,context){ImageRequest.Builder(context).data(model).memoryCacheKey("atlas:$model:$retry").build()}
    @Composable fun image(modifier: Modifier) {
        Box(modifier.clipToBounds().testTag("atlas-image-viewport").clickable(role=Role.Button,onClickLabel=text.fullscreen){fullscreen=true}.pointerInput(model) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 8f); offset += pan } },contentAlignment=Alignment.Center) {
            AsyncImage(request, title, Modifier.fillMaxSize().testTag("atlas-image").graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit,
                onLoading={state=0},onSuccess={state=1},onError={state=2})
            if(state==0)CircularProgressIndicator(Modifier.testTag("atlas-image-loading"))
            if(state==2)Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text(text.error,Modifier.testTag("atlas-image-error"))
                TextButton(onClick={retry++;onRetry?.invoke()},modifier=Modifier.testTag("atlas-image-retry")){Text(text.retry)}
            }
            if(state==1)Box(Modifier.size(1.dp).testTag("atlas-image-ready"))
        }
    }
    Row { TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text(text.reset) }; TextButton(onClick = { fullscreen = true }) { Text(text.fullscreen) } }
    Slider(modifier=Modifier.testTag("atlas-image-zoom"),value = scale, onValueChange = { scale = it }, valueRange = 1f..8f)
    image(Modifier.fillMaxWidth().height(320.dp))
    if(fullscreen) Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("atlas-image-fullscreen")) { Column { TextButton(onClick = { fullscreen = false }) { Text(text.back) }; image(Modifier.fillMaxWidth().weight(1f)) } }
    }
}
