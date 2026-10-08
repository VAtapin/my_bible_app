package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.CalendarIcon
import kotlinx.coroutines.launch
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.myapp.data.isConnected

@Composable
internal fun CalendarImage(model: String?, description: String, language: String, modifier: Modifier, previewFallback: String? = null) {
    val context = LocalContext.current
    var local by remember(model, previewFallback) { mutableStateOf<String?>(null) }
    LaunchedEffect(model, previewFallback) {
        val store = OfflineStore(context)
        local = model?.let { store.image(it)?.absolutePath } ?: previewFallback?.let { store.image(it)?.absolutePath }
    }
    val displayModel = if (model?.startsWith("https:") == true && (!isConnected(context) || model.contains("preview=1"))) local ?: model else model
    if (displayModel == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text(localized(R.string.calendar_image_unavailable, language)) }
    } else SubcomposeAsyncImage(model = displayModel, contentDescription = description,
        modifier = modifier, contentScale = ContentScale.Fit,
        success = { SubcomposeAsyncImageContent(Modifier.testTag("calendar-image-ready")) },
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
        error = {
            if (local != null && displayModel != local) SubcomposeAsyncImage(model = local, contentDescription = description,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(localized(R.string.calendar_image_unavailable, language)) }
        })
}

@Composable
internal fun CalendarIcons(icons: List<CalendarIcon>, language: String, detailed: Boolean) {
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = if (detailed) icons else icons.take(3)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visible.forEach { icon ->
            Column(Modifier.width(156.dp).clickable { openedId = icon.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                CalendarImage(calendarImageUrl(icon.imagePreviewUrl), icon.title, language, Modifier.fillMaxWidth().height(160.dp))
                Text(icon.title, Modifier.fillMaxWidth().padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
    icons.firstOrNull { it.id == openedId }?.let { icon ->
        CalendarIconGallery(icon, language) { openedId = null }
    }
}

@Composable
internal fun CalendarIconGallery(icon: CalendarIcon, language: String, onClose: () -> Unit) {
    val urls = remember(icon) { calendarGalleryUrls(icon) }
    val pager = rememberPagerState(pageCount = { urls.size })
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f)) {
            val imageHeight = (maxHeight * 0.65f).coerceIn(180.dp, 640.dp)
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(icon.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, localized(R.string.calendar_close_gallery, language)) }
                    }
                    if (urls.isEmpty()) Text(localized(R.string.calendar_image_unavailable, language))
                    else {
                        HorizontalPager(pager, Modifier.fillMaxWidth().height(imageHeight).testTag("calendar-gallery")) { index ->
                            val preview = if (urls[index] == calendarImageUrl(icon.imageUrl)) icon.imagePreviewUrl
                                else icon.images.firstOrNull { calendarImageUrl(it.url) == urls[index] }?.previewUrl
                            CalendarImage(urls[index], icon.title, language, Modifier.fillMaxSize(), calendarImageUrl(preview))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = urls.size > 1, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1 + urls.size) % urls.size) } }) {
                                Icon(Icons.Outlined.ChevronLeft, localized(R.string.calendar_previous_image, language))
                            }
                            Text("${pager.currentPage + 1} / ${urls.size}", Modifier.testTag("calendar-gallery-counter"))
                            IconButton(enabled = urls.size > 1, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % urls.size) } }) {
                                Icon(Icons.Outlined.ChevronRight, localized(R.string.calendar_next_image, language))
                            }
                        }
                    }
                    icon.dates.map { it.label }.distinct().forEach { Text(it) }
                    icon.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    icon.credit?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
