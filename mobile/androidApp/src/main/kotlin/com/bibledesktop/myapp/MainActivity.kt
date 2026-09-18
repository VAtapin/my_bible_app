package com.bibledesktop.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.TranslationSummary

private val Navy = Color(0xFF294A65)
private val PrimaryBlue = Color(0xFF4A6B8A)
private val LightBlue = Color(0xFF7DA1C2)
private val Cream = Color(0xFFF7F5F1)
private val WarmBorder = Color(0xFFEDE6D6)
private val Gold = Color(0xFFB99A5A)

private enum class LanguageFilter(val apiCode: String?) {
    All(null),
    Russian("ru"),
    German("de"),
}

private sealed interface TranslationState {
    data object Loading : TranslationState
    data class Content(val translations: List<TranslationSummary>) : TranslationState
    data object Error : TranslationState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BibleDesktopTheme {
                TranslationCatalogScreen()
            }
        }
    }
}

@Composable
private fun BibleDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryBlue,
            onPrimary = Color.White,
            secondary = LightBlue,
            tertiary = Gold,
            background = Cream,
            surface = Color.White,
            onBackground = Navy,
            onSurface = Navy,
            outline = WarmBorder,
        ),
        content = content,
    )
}

@Composable
private fun TranslationCatalogScreen() {
    val client = remember { BibleApiClient() }
    var filter by remember { mutableStateOf(LanguageFilter.All) }
    var state by remember { mutableStateOf<TranslationState>(TranslationState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    DisposableEffect(client) {
        onDispose(client::close)
    }

    LaunchedEffect(filter, reloadKey) {
        state = TranslationState.Loading
        state = runCatching { client.getTranslations(filter.apiCode) }
            .fold(
                onSuccess = { TranslationState.Content(it) },
                onFailure = { TranslationState.Error },
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Navy,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Gold),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.translations_title),
                color = Navy,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.translations_description),
                modifier = Modifier.padding(top = 5.dp),
                color = PrimaryBlue,
                style = MaterialTheme.typography.bodyMedium,
            )
            LanguageFilters(
                selected = filter,
                onSelected = { filter = it },
            )
        }

        when (val current = state) {
            TranslationState.Loading -> LoadingState()
            is TranslationState.Content -> TranslationList(current.translations)
            TranslationState.Error -> ErrorState(onRetry = { reloadKey += 1 })
        }
    }
}

@Composable
private fun LanguageFilters(
    selected: LanguageFilter,
    onSelected: (LanguageFilter) -> Unit,
) {
    val labels = mapOf(
        LanguageFilter.All to stringResource(R.string.language_all),
        LanguageFilter.Russian to stringResource(R.string.language_russian),
        LanguageFilter.German to stringResource(R.string.language_german),
    )

    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageFilter.entries.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(labels.getValue(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = PrimaryBlue,
                    selectedContainerColor = LightBlue.copy(alpha = 0.22f),
                    selectedLabelColor = Navy,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == item,
                    borderColor = WarmBorder,
                    selectedBorderColor = PrimaryBlue,
                ),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryBlue)
            Text(
                text = stringResource(R.string.loading),
                modifier = Modifier.padding(top = 14.dp),
                color = PrimaryBlue,
            )
        }
    }
}

@Composable
private fun TranslationList(translations: List<TranslationSummary>) {
    if (translations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(stringResource(R.string.empty), color = PrimaryBlue)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(translations, key = TranslationSummary::code) { translation ->
            TranslationCard(translation)
        }
    }
}

@Composable
private fun TranslationCard(translation: TranslationSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = translation.name,
                    color = Navy,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(translation.shortName, translation.code)
                        .distinct()
                        .joinToString(" · "),
                    modifier = Modifier.padding(top = 4.dp),
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = translation.language.code.uppercase(),
                    color = LightBlue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (translation.isDefault) {
                    Text(
                        text = stringResource(R.string.default_translation),
                        modifier = Modifier.padding(top = 4.dp),
                        color = Gold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.load_error),
                color = Navy,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
