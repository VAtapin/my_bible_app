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

@Composable
internal fun TodayScreen(
    language: String,
    selectedSections: Set<String>,
    selectedTranslations: List<TranslationSummary>,
    onEdit: () -> Unit,
    onOpenBible: () -> Unit,
    onOpenPrayers: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(localized(R.string.app_name, language), color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(localized(R.string.my_day, language), color = PrimaryBlue, fontSize = 12.sp)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Settings, contentDescription = localized(R.string.manual_title, language), tint = Navy)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Navy),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text(localized(R.string.today_title, language), color = Color.White, fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(localized(R.string.today_quote, language), modifier = Modifier.padding(top = 12.dp), color = LightBlue, fontFamily = FontFamily.Serif, fontSize = 18.sp)
                    }
                }
            }
            items(sections.filter { it.id in selectedSections }) { section ->
                HomeSectionCard(
                    section = section,
                    language = language,
                    trailing = if (section.id == "bible") selectedTranslations.firstOrNull()?.shortName else null,
                    onClick = when (section.id) {
                        "bible" -> onOpenBible
                        "prayer" -> onOpenPrayers
                        "calendar" -> onOpenCalendar
                        else -> null
                    },
                )
            }
        }

        NavigationBar(
            containerColor = Color.White,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            val items = listOf(
                Triple(Icons.Outlined.Home, R.string.nav_today, true),
                Triple(Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_bible, false),
                Triple(Icons.Outlined.Church, R.string.nav_prayers, false),
                Triple(Icons.Outlined.CalendarMonth, R.string.nav_calendar, false),
                Triple(Icons.Outlined.Menu, R.string.nav_more, false),
            )
            items.forEach { (icon, label, selected) ->
                NavigationBarItem(
                    selected = selected,
                    onClick = when (label) {
                        R.string.nav_bible -> onOpenBible
                        R.string.nav_prayers -> onOpenPrayers
                        R.string.nav_calendar -> onOpenCalendar
                        R.string.nav_more -> onOpenMore
                        else -> ({ })
                    },
                    enabled = selected || when (label) {
                        R.string.nav_bible -> "bible" in selectedSections
                        R.string.nav_prayers -> "prayer" in selectedSections
                        R.string.nav_calendar -> "calendar" in selectedSections
                        R.string.nav_more -> true
                        else -> false
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(localized(label, language), fontSize = 10.sp) },
                )
            }
        }
    }
}

@Composable
internal fun SetupScaffold(
    language: String,
    step: Int,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    scrollContent: Boolean = true,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = localized(R.string.action_back, language), tint = Navy)
            }
            Text(
                text = localized(R.string.step_format, language, step),
                modifier = Modifier.weight(1f),
                color = PrimaryBlue,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(48.dp))
        }
        LinearProgressIndicator(
            progress = { step / 3f },
            modifier = Modifier.fillMaxWidth(),
            color = Navy,
            trackColor = LightBlue,
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(title, color = Ink, fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, modifier = Modifier.padding(top = 6.dp), color = PrimaryBlue, fontSize = 13.sp)
        }

        val bodyModifier = Modifier
            .weight(1f)
            .padding(horizontal = 20.dp)
        Column(
            modifier = if (scrollContent) bodyModifier.verticalScroll(rememberScrollState()) else bodyModifier,
            content = content,
        )
        Surface(
            color = Cream,
            shadowElevation = 5.dp,
        ) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                footer()
            }
        }
    }
}

@Composable
internal fun LanguagePicker(language: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("ru" to "Русский", "de" to "Deutsch").forEach { (code, title) ->
            FilterChip(
                selected = language == code,
                onClick = { onChange(code) },
                label = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                leadingIcon = if (language == code) {
                    { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White,
                    labelColor = Ink,
                    selectedContainerColor = Navy,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
            )
        }
    }
}

@Composable
internal fun SectionToggleRow(
    section: SectionOption,
    language: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(LightBlue, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.icon, contentDescription = null, tint = Navy)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(localized(section.title, language), color = Ink, fontWeight = FontWeight.Bold)
                Text(localized(section.subtitle, language), color = PrimaryBlue, fontSize = 11.sp)
            }
            Switch(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
internal fun TranslationFilters(
    language: String,
    selected: TranslationFilter,
    onChange: (TranslationFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf(
            TranslationFilter.All to localized(R.string.language_all, language),
            TranslationFilter.Russian to "Русский",
            TranslationFilter.German to "Deutsch",
        )
        options.forEach { (filter, title) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onChange(filter) },
                label = { Text(title) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White,
                    labelColor = Ink,
                    selectedContainerColor = Navy,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
internal fun TranslationSelectionRow(
    translation: TranslationSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) LightBlue else Color.White),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (selected) Navy else PrimaryBlue, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(translation.language.code.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(translation.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    listOfNotNull(translation.shortName, translation.language.name).distinct().joinToString(" · "),
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                )
            }
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}

@Composable
internal fun SummaryGroup(title: String, values: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold)
            values.forEachIndexed { index, value ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = WarmBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Text(value, modifier = Modifier.padding(start = 9.dp), color = Ink, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionCard(
    section: SectionOption,
    language: String,
    trailing: String?,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(LightBlue, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.icon, contentDescription = null, tint = Navy)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(localized(section.title, language), color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    trailing ?: localized(section.subtitle, language),
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                )
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = PrimaryBlue)
            }
        }
    }
}

@Composable
internal fun localized(@StringRes id: Int, language: String, vararg args: Any): String {
    val context = LocalContext.current
    return remember(id, language, args.toList()) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
