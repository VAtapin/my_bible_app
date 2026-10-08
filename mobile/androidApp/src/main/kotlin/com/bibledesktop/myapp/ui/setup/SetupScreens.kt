package com.bibledesktop.myapp.ui.setup

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.BibleReader
import com.bibledesktop.myapp.ui.daily.CalendarScreen
import com.bibledesktop.myapp.ui.daily.CalendarOverview
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Gold
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.TranslationSummary
import java.util.Locale

@Composable
internal fun WelcomeScreen(
    language: String,
    client: BibleContentSource,
    translationsState: TranslationState,
    onRetry: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onQuick: () -> Unit,
    onManual: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
    val split = maxWidth >= 840.dp && maxWidth > maxHeight
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) { LanguagePicker(language, onLanguageChange) }
        Box(Modifier.fillMaxWidth()) {
            if (split) Row(Modifier.fillMaxWidth().testTag("welcome-split")) {
                WelcomeHero(language, Modifier.weight(1f))
                Column(Modifier.weight(1f).padding(24.dp)) {
                    WelcomeActions(language, translationsState, onRetry, onQuick, onManual)
                }
            } else Column(Modifier.fillMaxWidth().testTag("welcome-stacked")) {
                WelcomeHero(language, Modifier.fillMaxWidth())
                Column(Modifier.padding(18.dp)) {
                    WelcomeActions(language, translationsState, onRetry, onQuick, onManual)
                }
            }
        }
        CalendarOverview(language, client, Modifier.padding(18.dp))
    }
    }
}

@Composable
private fun WelcomeHero(language: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = (maxWidth * 0.8f).coerceIn(260.dp, 480.dp)
            Box(Modifier.fillMaxWidth().height(height).testTag("welcome-hero")) {
                Image(painterResource(R.drawable.welcome_church), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.36f))
                Box(Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Cream))))
            }
        }
        Text(localized(R.string.app_name, language), color = Ink, fontFamily = FontFamily.Serif,
            fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(localized(R.string.welcome_subtitle, language), Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = PrimaryBlue, fontFamily = FontFamily.Serif, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WelcomeActions(language: String, state: TranslationState, onRetry: () -> Unit, onQuick: () -> Unit, onManual: () -> Unit) {
        Text(localized(R.string.welcome_title, language), color = Ink, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
        ActionCard(
            title = localized(R.string.quick_title, language),
            subtitle = localized(R.string.quick_subtitle, language),
            icon = Icons.Outlined.Bolt,
            primary = true,
            enabled = state is TranslationState.Content && com.bibledesktop.shared.presentation.recommendedNativeTranslations(state.translations, language).isNotEmpty(),
            onClick = onQuick,
        )

        if (state is TranslationState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
        if (state is TranslationState.Error) {
            Text(localized(R.string.load_error, language), color = Ink)
            TextButton(onClick = onRetry) { Text(localized(R.string.retry, language)) }
        }
        ActionCard(
            title = localized(R.string.manual_title, language),
            subtitle = localized(R.string.manual_subtitle, language),
            icon = Icons.Outlined.Settings,
            onClick = onManual,
        )
        ActionCard(
            title = localized(R.string.restore_title, language),
            subtitle = localized(R.string.restore_subtitle, language),
            icon = Icons.Outlined.Restore,
            enabled = false,
            onClick = {},
        )

        Text(
            text = localized(R.string.welcome_quote, language),
            modifier = Modifier.padding(top = 26.dp),
            color = PrimaryBlue,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
}
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (primary) Navy else Color.White
    val foreground = if (primary) Color.White else Ink
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = background,
            disabledContainerColor = Color.White.copy(alpha = 0.55f),
        ),
        border = if (primary) null else CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) foreground else PrimaryBlue)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(title, color = foreground, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (primary) LightBlue else PrimaryBlue, fontSize = 12.sp)
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = if (enabled) foreground else PrimaryBlue,
            )
        }
    }
}

@Composable
internal fun SectionsScreen(
    language: String,
    selected: Set<String>,
    onLanguageChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    SetupScaffold(
        language = language,
        step = 1,
        title = localized(R.string.sections_title, language),
        subtitle = localized(R.string.sections_subtitle, language),
        onBack = onBack,
        footer = {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(localized(R.string.action_next, language))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        },
    ) {
        Text(
            text = localized(R.string.language_title, language),
            color = Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LanguagePicker(language, onLanguageChange)
        Spacer(Modifier.height(22.dp))

        sections.forEach { section ->
            SectionToggleRow(
                section = section,
                language = language,
                selected = section.id in selected,
                onToggle = { onToggle(section.id) },
            )
        }
    }
}

@Composable
internal fun TranslationsScreen(
    language: String,
    state: TranslationState,
    filter: TranslationFilter,
    selectedCodes: Set<String>,
    onFilterChange: (TranslationFilter) -> Unit,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    SetupScaffold(
        language = language,
        step = 2,
        title = localized(R.string.setup_translations_title, language),
        subtitle = localized(R.string.setup_translations_subtitle, language),
        onBack = onBack,
        scrollContent = false,
        footer = {
            Button(
                onClick = onNext,
                enabled = selectedCodes.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(localized(R.string.action_next, language))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        },
    ) {
        TranslationFilters(language, filter, onFilterChange)
        Text(
            text = localized(R.string.selected_count, language, selectedCodes.size),
            modifier = Modifier.padding(vertical = 10.dp),
            color = PrimaryBlue,
            fontSize = 12.sp,
        )

        when (state) {
            TranslationState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            TranslationState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(localized(R.string.load_error, language), color = Ink)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text(localized(R.string.retry, language))
                }
            }
            is TranslationState.Content -> {
                val visible = state.translations.filter {
                    filter.code == null || it.language.code == filter.code
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = TranslationSummary::code) { translation ->
                        TranslationSelectionRow(
                            translation = translation,
                            selected = translation.code in selectedCodes,
                            onClick = { onToggle(translation.code) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryScreen(
    language: String,
    selectedSections: Set<String>,
    selectedTranslations: List<TranslationSummary>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
) {
    SetupScaffold(
        language = language,
        step = 3,
        title = localized(R.string.summary_title, language),
        subtitle = localized(R.string.summary_subtitle, language),
        onBack = onBack,
        footer = {
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(localized(R.string.create_app, language), modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) {
        SummaryGroup(
            title = localized(R.string.summary_language, language),
            values = listOf(com.bibledesktop.shared.presentation.interfaceLanguages.getValue(language)),
        )
        SummaryGroup(
            title = localized(R.string.summary_sections, language),
            values = sections.filter { it.id in selectedSections }.map { localized(it.title, language) },
        )
        if ("bible" in selectedSections) {
            SummaryGroup(
                title = localized(R.string.summary_translations, language),
                values = selectedTranslations.map(TranslationSummary::name),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Navy)
            Text(
                text = localized(R.string.offline_after_create, language),
                modifier = Modifier.padding(start = 12.dp),
                color = PrimaryBlue,
                fontSize = 13.sp,
            )
        }
    }
}
