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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Gold
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.TranslationSummary
import java.util.Locale

private const val PreferencesName = "bible-desktop-native-profile"

private enum class Route {
    Welcome,
    Sections,
    Translations,
    Summary,
    Home,
    Bible,
    Prayers,
    Calendar,
}

internal enum class TranslationFilter(val code: String?) {
    All(null),
    Russian("ru"),
    German("de"),
}

internal sealed interface TranslationState {
    data object Loading : TranslationState
    data class Content(val translations: List<TranslationSummary>) : TranslationState
    data object Error : TranslationState
}

internal data class SectionOption(
    val id: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
)

internal val sections = listOf(
    SectionOption("bible", R.string.section_bible, R.string.section_bible_subtitle, Icons.AutoMirrored.Outlined.MenuBook),
    SectionOption("prayer", R.string.section_prayer, R.string.section_prayer_subtitle, Icons.Outlined.Church),
    SectionOption("calendar", R.string.section_calendar, R.string.section_calendar_subtitle, Icons.Outlined.CalendarMonth),
    SectionOption("study", R.string.section_study, R.string.section_study_subtitle, Icons.Outlined.AutoStories),
    SectionOption("reminders", R.string.section_reminders, R.string.section_reminders_subtitle, Icons.Outlined.NotificationsNone),
)

@Composable
fun SetupApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }
    val initialLanguage = remember {
        preferences.getString("uiLanguage", null)
            ?: if (Locale.getDefault().language == "de") "de" else "ru"
    }

    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var selectedSectionIds by rememberSaveable {
        mutableStateOf(preferences.getString("sections", "bible,prayer,calendar").orEmpty())
    }
    var selectedTranslationCodes by rememberSaveable {
        mutableStateOf(preferences.getString("translations", "").orEmpty())
    }
    var route by rememberSaveable {
        mutableStateOf(
            if (preferences.getBoolean("setupComplete", false)) Route.Home else Route.Welcome,
        )
    }
    var translationFilter by rememberSaveable { mutableStateOf(TranslationFilter.All) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var translationState by remember { mutableStateOf<TranslationState>(TranslationState.Loading) }
    val client = remember { BibleApiClient() }

    val selectedSections = remember(selectedSectionIds) {
        selectedSectionIds.split(',').filter(String::isNotBlank).toSet()
    }
    val selectedTranslations = remember(selectedTranslationCodes) {
        selectedTranslationCodes.split(',').filter(String::isNotBlank).toSet()
    }

    DisposableEffect(client) {
        onDispose(client::close)
    }

    LaunchedEffect(reloadKey) {
        translationState = TranslationState.Loading
        translationState = runCatching { client.getTranslations() }
            .fold(
                onSuccess = { TranslationState.Content(it) },
                onFailure = { TranslationState.Error },
            )
    }

    LaunchedEffect(route, translationState, language) {
        if (route == Route.Translations && selectedTranslations.isEmpty()) {
            val available = (translationState as? TranslationState.Content)?.translations.orEmpty()
            val recommended = available.firstOrNull { it.language.code == language && it.isDefault }
                ?: available.firstOrNull { it.language.code == language }
                ?: available.firstOrNull()
            recommended?.let { selectedTranslationCodes = it.code }
        }
    }

    BackHandler(
        enabled = route == Route.Sections || route == Route.Translations || route == Route.Summary,
    ) {
        route = when (route) {
            Route.Sections -> Route.Welcome
            Route.Translations -> Route.Sections
            Route.Summary -> if ("bible" in selectedSections) Route.Translations else Route.Sections
            else -> route
        }
    }

    when (route) {
        Route.Welcome -> WelcomeScreen(
            language = language,
            onLanguageChange = { language = it },
            onQuick = {
                selectedSectionIds = "bible,prayer,calendar"
                route = Route.Translations
            },
            onManual = { route = Route.Sections },
        )

        Route.Sections -> SectionsScreen(
            language = language,
            selected = selectedSections,
            onLanguageChange = { language = it },
            onToggle = { id -> selectedSectionIds = toggleCsv(selectedSections, id) },
            onBack = { route = Route.Welcome },
            onNext = {
                route = if ("bible" in selectedSections) Route.Translations else Route.Summary
            },
        )

        Route.Translations -> TranslationsScreen(
            language = language,
            state = translationState,
            filter = translationFilter,
            selectedCodes = selectedTranslations,
            onFilterChange = { translationFilter = it },
            onToggle = { code -> selectedTranslationCodes = toggleCsv(selectedTranslations, code) },
            onBack = { route = Route.Sections },
            onRetry = { reloadKey += 1 },
            onNext = { route = Route.Summary },
        )

        Route.Summary -> SummaryScreen(
            language = language,
            selectedSections = selectedSections,
            selectedTranslations = (translationState as? TranslationState.Content)
                ?.translations
                .orEmpty()
                .filter { it.code in selectedTranslations }
                .sortedBy { if (it.language.code == language) 0 else 1 },
            onBack = {
                route = if ("bible" in selectedSections) Route.Translations else Route.Sections
            },
            onCreate = {
                preferences.edit()
                    .putBoolean("setupComplete", true)
                    .putString("uiLanguage", language)
                    .putString("sections", selectedSectionIds)
                    .putString("translations", selectedTranslationCodes)
                    .apply()
                route = Route.Home
            },
        )

        Route.Home -> TodayScreen(
            language = language,
            selectedSections = selectedSections,
            selectedTranslations = (translationState as? TranslationState.Content)
                ?.translations
                .orEmpty()
                .filter { it.code in selectedTranslations }
                .sortedBy { if (it.language.code == language) 0 else 1 },
            onEdit = { route = Route.Sections },
            onOpenBible = { route = Route.Bible },
            onOpenPrayers = { route = Route.Prayers },
            onOpenCalendar = { route = Route.Calendar },
        )

        Route.Bible -> BibleReader(
            language = language,
            translations = (translationState as? TranslationState.Content)
                ?.translations
                .orEmpty()
                .filter { it.code in selectedTranslations }
                .sortedBy { if (it.language.code == language) 0 else 1 },
            client = client,
            onBack = { route = Route.Home },
        )

        Route.Prayers -> PrayersScreen(
            language = language,
            client = client,
            onBack = { route = Route.Home },
        )

        Route.Calendar -> CalendarScreen(
            language = language,
            client = client,
            onBack = { route = Route.Home },
        )
    }
}

private fun toggleCsv(selected: Set<String>, id: String): String {
    val result = selected.toMutableSet().apply {
        if (!add(id)) remove(id)
    }
    return result.sorted().joinToString(",")
}
