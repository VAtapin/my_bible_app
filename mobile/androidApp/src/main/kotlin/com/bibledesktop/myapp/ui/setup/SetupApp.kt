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
import com.bibledesktop.myapp.ui.bible.BookmarkEntry
import com.bibledesktop.myapp.ui.daily.CalendarScreen
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.more.MoreScreen
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Gold
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.myapp.data.OfflineContentRepository
import com.bibledesktop.myapp.data.ReaderLink
import com.bibledesktop.shared.api.TranslationSummary
import com.bibledesktop.shared.presentation.initialInterfaceLanguage
import com.bibledesktop.shared.presentation.quickNativeSections
import com.bibledesktop.shared.presentation.recommendedNativeTranslations
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
    More,
    Study,
    Reminders,
    BibleLibrary,
    LinkedBible,
    Books,
    Search,
    Dictionaries,
    PersonalStudy,
    StudyDownloads,
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
fun SetupApp(initialDestination: String? = null, initialReaderLink: ReaderLink? = null) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }
    val initialLanguage = remember {
        initialInterfaceLanguage(preferences.getString("uiLanguage", null), Locale.getDefault().language)
    }

    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var setupComplete by rememberSaveable {
        mutableStateOf(preferences.getBoolean("setupComplete", false))
    }
    var quickSetup by rememberSaveable { mutableStateOf(false) }
    var selectedSectionIds by rememberSaveable {
        mutableStateOf(preferences.getString("sections", "bible,prayer,calendar").orEmpty())
    }
    var selectedTranslationCodes by rememberSaveable {
        mutableStateOf(preferences.getString("translations", "").orEmpty())
    }
    var route by rememberSaveable {
        mutableStateOf(
            if (initialReaderLink != null) Route.LinkedBible else if (!setupComplete) Route.Welcome else when (initialDestination) {
                "prayer" -> Route.Prayers
                "bible" -> Route.Bible
                "calendar" -> Route.Calendar
                else -> Route.Home
            },
        )
    }
    var libraryReturn by rememberSaveable { mutableStateOf(Route.Home) }
    var installedTranslations by remember { mutableStateOf<List<TranslationSummary>>(emptyList()) }
    var chooseBiblePassage by rememberSaveable { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var translationState by remember { mutableStateOf<TranslationState>(TranslationState.Loading) }
    val client = remember { OfflineContentRepository(context.applicationContext) }

    LaunchedEffect(route) { installedTranslations = client.installedTranslations() }

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

    BackHandler(
        enabled = route == Route.Sections || route == Route.Translations || route == Route.Summary,
    ) {
        route = when (route) {
            Route.Sections -> if (setupComplete) Route.Home else Route.Welcome
            Route.Translations -> if (quickSetup) Route.Welcome else Route.Sections
            Route.Summary -> if ("bible" in selectedSections) Route.Translations else Route.Sections
            else -> route
        }
    }

    val changeLanguage: (String) -> Unit = {
        language = it
        preferences.edit().putString("uiLanguage", it).apply()
    }

    when (route) {
        Route.Welcome -> WelcomeScreen(
            language = language,
            client = client,
            translationsState = translationState,
            onRetry = { reloadKey += 1 },
            onSettings = { quickSetup = false; route = Route.Sections },
            onQuick = {
                val available = (translationState as? TranslationState.Content)?.translations.orEmpty()
                val recommended = recommendedNativeTranslations(available, language)
                if (recommended.isNotEmpty()) {
                    selectedSectionIds = quickNativeSections.joinToString(",")
                    selectedTranslationCodes = recommended.joinToString(",")
                    preferences.edit().putBoolean("setupComplete", true)
                        .putString("uiLanguage", language).putString("sections", selectedSectionIds)
                        .putString("translations", selectedTranslationCodes).apply()
                    setupComplete = true
                    route = Route.Home
                }
            },
            onManual = {
                quickSetup = false
                route = Route.Sections
            },
        )

        Route.Sections -> SectionsScreen(
            language = language,
            selected = selectedSections,
            onLanguageChange = changeLanguage,
            onToggle = { id -> selectedSectionIds = toggleCsv(selectedSections, id) },
            onBack = { route = if (setupComplete) Route.Home else Route.Welcome },
            onNext = {
                quickSetup = false
                route = if ("bible" in selectedSections) Route.Translations else Route.Summary
            },
        )

        Route.Translations -> TranslationsScreen(
            language = language,
            source = client,
            onInstalled = { installedTranslations = it; selectedTranslationCodes = it.joinToString(",") { edition -> edition.code } },
            onBack = { route = if (quickSetup) Route.Welcome else Route.Sections },
            onNext = { route = Route.Summary },
        )

        Route.Summary -> SummaryScreen(
            language = language,
            selectedSections = selectedSections,
            selectedTranslations = installedTranslations
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
                setupComplete = true
                route = Route.Home
            },
        )

        Route.Home -> TodayScreen(
            language = language,
            client = client,
            selectedSections = selectedSections,
            selectedTranslations = installedTranslations
                .sortedBy { if (it.language.code == language) 0 else 1 },
            onEdit = {
                quickSetup = false
                route = Route.Sections
            },
            onOpenBible = { route = Route.Bible },
            onOpenPrayers = { route = Route.Prayers },
            onOpenCalendar = { route = Route.Calendar },
            onOpenMore = { route = Route.More },
            onOpenStudy = { route = Route.Study },
            onOpenReminders = { route = Route.Reminders },
        )

        Route.Bible -> BibleReader(
            language = language,
            translations = installedTranslations
                .sortedBy { if (it.language.code == language) 0 else 1 },
            client = client,
            onBack = { chooseBiblePassage = false; route = Route.Home },
            onDownloads = { chooseBiblePassage = false; libraryReturn = Route.Bible; route = Route.BibleLibrary },
            choosePassageOnOpen = chooseBiblePassage,
        )

        Route.LinkedBible -> initialReaderLink?.let { link ->
            com.bibledesktop.myapp.ui.bible.LinkedBibleReader(language, link, client,
                onBack = { route = if (setupComplete) Route.Home else Route.Welcome },
                onDownloads = { libraryReturn = Route.LinkedBible; route = Route.BibleLibrary })
        }

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

        Route.More -> MoreScreen(
            language = language,
            onOpenBooks = { route = Route.Books },
            onSearch = { route = Route.Search },
            onDictionaries = { route = Route.Dictionaries },
            onPersonalStudy = { route = Route.PersonalStudy },
            onStudyDownloads = { route = Route.StudyDownloads },
            onBack = { route = Route.Home },
            onSettings = {
                quickSetup = false
                route = Route.Sections
            },
            onOpenStudy = { route = Route.Study },
            onOpenReminders = { route = Route.Reminders },
            onBibleDownloads = { libraryReturn = Route.More; route = Route.BibleLibrary },
            onOpenBookmark = { bookmark: BookmarkEntry ->
                preferences.edit()
                    .putString("lastTranslation", bookmark.translationCode)
                    .putString("lastBookSlug", bookmark.bookSlug)
                    .putInt("lastChapter", bookmark.chapter)
                    .putInt("lastVerse", bookmark.verse)
                    .putInt("lastVerseOffset", 0)
                    .apply()
                route = Route.Bible
            },
        )
        Route.Study -> com.bibledesktop.myapp.ui.study.StudyScreen(language, client,
            onBack = { route = Route.Home }, onBible = { chooseBiblePassage = true; route = Route.Bible }, onOpen = { passage ->
                preferences.edit().putString("lastTranslation", passage.translationCode).putString("lastBookSlug", passage.bookSlug)
                    .putInt("lastChapter", passage.chapter).putInt("lastVerse", passage.verse).putInt("lastVerseOffset", 0).apply()
                route = Route.Bible
            })
        Route.Books -> com.bibledesktop.myapp.ui.study.BooksScreen(language, client, onBack = { route = Route.More })
        Route.Dictionaries -> com.bibledesktop.myapp.ui.study.DictionaryLibrary(language, client, preferences.getString("lastTranslation", "").orEmpty(), onBack = { route = Route.More })
        Route.StudyDownloads -> com.bibledesktop.myapp.ui.study.DictionaryLibrary(language, client, preferences.getString("lastTranslation", "").orEmpty(), onBack = { route = Route.More }, downloads = true)
        Route.PersonalStudy -> com.bibledesktop.myapp.ui.bible.PersonalStudyLibrary(language, onBack = { route = Route.More }, onOpen = { passage ->
            preferences.edit().putString("lastTranslation", passage.translationCode).putString("lastBookSlug", passage.bookSlug)
                .putInt("lastChapter", passage.start.chapter).putInt("lastVerse", passage.start.verse).putInt("lastVerseOffset", 0).apply()
            chooseBiblePassage = false; route = Route.Bible
        }, onReminders = { route = Route.Reminders })
        Route.Search -> com.bibledesktop.myapp.ui.bible.BibleSearchScreen(language, client,
            preferences.getString("lastTranslation", "").orEmpty(), onBack = { route = Route.More }, onOpen = { hit ->
                preferences.edit().putString("lastTranslation", hit.translation).putString("lastBookSlug",hit.book)
                    .putInt("lastChapter",hit.chapter).putInt("lastVerse",hit.verse).putInt("lastVerseOffset",0).apply()
                chooseBiblePassage=false;route=Route.Bible
            })
        Route.Reminders -> com.bibledesktop.myapp.ui.reminders.RemindersScreen(language) { route = Route.Home }
        Route.BibleLibrary -> com.bibledesktop.myapp.ui.more.BibleLibraryScreen(language, client,
            onBack = { route = libraryReturn }, onOpen = { code ->
                preferences.edit().putString("lastTranslation", code).remove("lastBookSlug").remove("lastChapter").remove("lastVerse").apply()
                route = Route.Bible
            })
    }
}

private fun toggleCsv(selected: Set<String>, id: String): String {
    val result = selected.toMutableSet().apply {
        if (!add(id)) remove(id)
    }
    return result.sorted().joinToString(",")
}
