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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Gold
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.CalendarDay
import com.bibledesktop.shared.api.PrayerDetail
import com.bibledesktop.shared.api.PrayerSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Error : LoadState<Nothing>
}

@Composable
fun PrayersScreen(
    language: String,
    client: BibleApiClient,
    onBack: () -> Unit,
) {
    var selectedPrayer by rememberSaveable { mutableStateOf<Long?>(null) }
    var listState by remember { mutableStateOf<LoadState<List<PrayerSummary>>>(LoadState.Loading) }
    var detailState by remember { mutableStateOf<LoadState<PrayerDetail>>(LoadState.Loading) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(language, retry) {
        listState = LoadState.Loading
        listState = runCatching { client.getPrayers(language) }
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
        ContentListPage(title = localText(R.string.prayers_title, language), onBack = onBack) {
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
                Text(prayer.title, color = Ink, fontWeight = FontWeight.Bold)
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
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var fontSize by rememberSaveable { mutableFloatStateOf(19f) }
    val title = (state as? LoadState.Ready)?.value?.title ?: localText(R.string.prayers_title, language)

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        Header(title, onBack)
        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                localText(R.string.prayer_error, language),
                localText(R.string.retry, language),
                onRetry,
                Modifier.weight(1f),
            )
            is LoadState.Ready -> Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),
            ) {
                current.value.intro?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = PrimaryBlue, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    current.value.body,
                    color = Ink,
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5f).sp,
                )
            }
        }
        Surface(color = Color.White, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(15f) }, enabled = fontSize > 15f) {
                    Icon(Icons.Outlined.TextDecrease, contentDescription = null)
                }
                IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28f) }, enabled = fontSize < 28f) {
                    Icon(Icons.Outlined.TextIncrease, contentDescription = null)
                }
                IconButton(
                    onClick = {
                        val prayer = (state as? LoadState.Ready)?.value ?: return@IconButton
                        share(context, "${prayer.title}\n\n${prayer.body}")
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
    client: BibleApiClient,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var dateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var state by remember { mutableStateOf<LoadState<CalendarDay>>(LoadState.Loading) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(dateIso, language, retry) {
        state = LoadState.Loading
        state = runCatching { client.getCalendarDay(dateIso, language) }
            .fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Error },
            )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        Header(localText(R.string.calendar_title, language), onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { dateIso = LocalDate.parse(dateIso).minusDays(1).toString() }) {
                Icon(Icons.Outlined.ChevronLeft, localText(R.string.calendar_previous, language))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDate(dateIso, language), color = Ink, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                (state as? LoadState.Ready)?.value?.let {
                    Text(localText(R.string.calendar_old_style, language, formatDate(it.oldStyleDate, language)), color = PrimaryBlue, fontSize = 11.sp)
                }
            }
            IconButton(onClick = { dateIso = LocalDate.parse(dateIso).plusDays(1).toString() }) {
                Icon(Icons.Outlined.ChevronRight, localText(R.string.calendar_next, language))
            }
        }

        when (val current = state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                localText(R.string.calendar_error, language),
                localText(R.string.retry, language),
                { retry += 1 },
                Modifier.weight(1f),
            )
            is LoadState.Ready -> CalendarDayContent(language, current.value, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalendarDayContent(language: String, day: CalendarDay, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(day.liturgicalPeriod, color = Navy, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (day.fastingEvents.isNotEmpty()) {
            item {
                CalendarGroup(
                    title = localText(R.string.calendar_fasting, language),
                    values = day.fastingEvents.map { it.name },
                    highlighted = true,
                )
            }
        }
        if (day.events.isNotEmpty()) {
            item {
                CalendarGroup(
                    title = localText(R.string.calendar_events, language),
                    values = day.events.map { it.name },
                )
            }
        }
        if (day.readings.isNotEmpty()) {
            item {
                CalendarGroup(
                    title = localText(R.string.calendar_readings, language),
                    values = day.readings.map { it.displayRef.ifBlank { it.title } },
                )
            }
        }
        if (day.events.isEmpty() && day.fastingEvents.isEmpty() && day.readings.isEmpty()) {
            item { EmptyBox(localText(R.string.calendar_empty, language)) }
        }
    }
}

@Composable
private fun CalendarGroup(title: String, values: List<String>, highlighted: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlighted) LightBlue else Color.White),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold)
            values.forEachIndexed { index, value ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = WarmBorder)
                Text(value, color = Ink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ContentListPage(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        Header(title, onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            content = content,
        )
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Navy)
        }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = 48.dp),
            color = Ink,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
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

private fun formatDate(value: String, language: String): String = runCatching {
    LocalDate.parse(value).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.forLanguageTag(language)),
    )
}.getOrDefault(value)

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
    return remember(id, language, args.toList()) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
