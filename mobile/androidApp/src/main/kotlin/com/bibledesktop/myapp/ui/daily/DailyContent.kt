package com.bibledesktop.myapp.ui.daily

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.reading.ReadingViewport
import com.bibledesktop.myapp.ui.reading.PrayerReadingContent
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Gold
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.PrayerDetail
import com.bibledesktop.shared.api.PrayerSummary
import java.util.Locale

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Error : LoadState<Nothing>
}

@Composable
fun PrayersScreen(
    language: String,
    client: BibleContentSource,
    onBack: () -> Unit,
) {
    var selectedPrayer by rememberSaveable { mutableStateOf<Long?>(null) }
    var textLanguage by rememberSaveable { mutableStateOf(language) }
    var listState by remember { mutableStateOf<LoadState<List<PrayerSummary>>>(LoadState.Loading) }
    var detailState by remember { mutableStateOf<LoadState<PrayerDetail>>(LoadState.Loading) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(textLanguage, retry) {
        listState = LoadState.Loading
        listState = runCatching { client.getPrayers(textLanguage) }
            .fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Error },
            )
    }

    LaunchedEffect(selectedPrayer, retry) {
        val id = selectedPrayer ?: return@LaunchedEffect
        detailState = LoadState.Loading
        detailState = runCatching { client.getPrayer(id) }
            .fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Error },
            )
    }

    BackHandler {
        if (selectedPrayer != null) selectedPrayer = null else onBack()
    }

    if (selectedPrayer == null) {
        ContentListPage(title = localText(R.string.prayers_title, language), language = language, onBack = onBack) {
            item {
                Text(localText(R.string.prayer_text_language, language), color = Ink, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val editions = com.bibledesktop.shared.presentation.interfaceLanguages + linkedMapOf(
                        "cu" to localText(R.string.prayer_cu, language),
                        "cu-civil" to localText(R.string.prayer_cu_civil, language),
                    )
                    editions.forEach { (code, label) ->
                        FilterChip(selected = textLanguage == code, modifier = Modifier.testTag("prayer-edition-$code"),
                            onClick = { textLanguage = code }, label = { Text(label) })
                    }
                }
            }
            if (textLanguage in setOf("cu", "cu-civil") && listState is LoadState.Ready) item {
                Text(localText(R.string.prayer_source_count, language, (listState as LoadState.Ready).value.size),
                    Modifier.testTag("prayer-source-count").padding(vertical = 8.dp), color = PrimaryBlue)
            }
            when (val current = listState) {
                LoadState.Loading -> item { LoadingBox() }
                LoadState.Error -> item {
                    ErrorBox(
                        message = localText(R.string.prayers_error, language),
                        retryTitle = localText(R.string.retry, language),
                        onRetry = { retry += 1 },
                    )
                }
                is LoadState.Ready -> if (current.value.isEmpty()) {
                    item { EmptyBox(localText(R.string.prayers_empty_language, language)) }
                } else {
                    items(current.value, key = PrayerSummary::id) { prayer ->
                        PrayerCard(prayer) { selectedPrayer = prayer.id }
                    }
                }
            }
        }
    } else {
        PrayerReader(
            language = language,
            state = detailState,
            onBack = { selectedPrayer = null },
            onHome = onBack,
            onRetry = { retry += 1 },
        )
    }
}

@Composable
private fun PrayerCard(prayer: PrayerSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(LightBlue, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Church, contentDescription = null, tint = Navy)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(prayer.title, color = Ink, fontFamily = com.bibledesktop.myapp.ui.theme.readingFont(prayer.languageCode),
                    fontSize = 18.sp, lineHeight = 27.sp)
                Text(
                    prayer.excerpt,
                    color = PrimaryBlue,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            Text(prayer.languageCode.uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = PrimaryBlue)
        }
    }
}

@Composable
private fun PrayerReader(
    language: String,
    state: LoadState<PrayerDetail>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var fontSize by rememberSaveable { mutableFloatStateOf(19f) }
    val title = (state as? LoadState.Ready)?.value?.title ?: localText(R.string.prayers_title, language)

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        ReadingHeader(title, language, onBack, onHome)
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                localText(R.string.prayer_error, language),
                localText(R.string.retry, language),
                onRetry,
                Modifier.weight(1f),
            )
            is LoadState.Ready -> ReadingViewport(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PrayerReadingContent(current.value, fontSize)
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(15f) }, enabled = fontSize > 15f) {
                    Icon(Icons.Outlined.TextDecrease, localText(R.string.bible_font_smaller, language))
                }
                IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28f) }, enabled = fontSize < 28f) {
                    Icon(Icons.Outlined.TextIncrease, localText(R.string.bible_font_larger, language))
                }
                IconButton(
                    onClick = {
                        val prayer = (state as? LoadState.Ready)?.value ?: return@IconButton
                        share(context, "${prayer.title}\n\n${readingText(prayer.body)}")
                    },
                    enabled = state is LoadState.Ready,
                ) {
                    Icon(Icons.Outlined.Share, localText(R.string.prayer_share, language))
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(
    language: String,
    client: BibleContentSource,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        ReadingHeader(localText(R.string.calendar_title, language), language, onBack, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            CalendarOverview(language, client, detailed = true)
        }
    }
}

@Composable
private fun ContentListPage(
    title: String,
    language: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        ReadingHeader(title, language, onBack, onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            content = content,
        )
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier.fillMaxWidth().height(220.dp)) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Navy) }
}

@Composable
private fun EmptyBox(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Text(message, modifier = Modifier.padding(20.dp), color = PrimaryBlue)
    }
}

@Composable
private fun ErrorBox(
    message: String,
    retryTitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Ink)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(retryTitle) }
    }
}

private fun share(context: Context, body: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            },
            null,
        ),
    )
}

@Composable
private fun localText(@StringRes id: Int, language: String, vararg args: Any): String {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    return remember(id, language, args.toList(), currentConfiguration) {
        val configuration = Configuration(currentConfiguration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
