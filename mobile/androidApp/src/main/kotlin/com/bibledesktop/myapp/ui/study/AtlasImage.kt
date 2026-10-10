package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

@Composable internal fun AtlasImage(model: Any, title: String, language: String) {
    val text = dictionaryTexts(language)
    var fullscreen by remember { mutableStateOf(false) }
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    @Composable fun image(modifier: Modifier) {
        Box(modifier.pointerInput(model) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 8f); offset += pan } }) {
            AsyncImage(model, title, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
        }
    }
    Row { TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text(text.reset) }; TextButton(onClick = { fullscreen = true }) { Text(text.fullscreen) } }
    Slider(value = scale, onValueChange = { scale = it }, valueRange = 1f..8f)
    image(Modifier.fillMaxWidth().height(320.dp))
    if(fullscreen) Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column { TextButton(onClick = { fullscreen = false }) { Text(text.back) }; image(Modifier.fillMaxWidth().weight(1f)) } }
    }
}
