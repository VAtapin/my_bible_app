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
import com.bibledesktop.shared.api.PrayerCatalog
import com.bibledesktop.shared.api.validatePrayerCatalog
import com.bibledesktop.shared.api.validatePrayerDetail
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.CancellationException
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
    var selectedEdition by rememberSaveable { mutableStateOf<String?>(null) }
    var textLanguage by rememberSaveable { mutableStateOf("all") }
    var group by rememberSaveable { mutableStateOf("all") }
    var listState by remember { mutableStateOf<LoadState<PrayerCatalog>>(LoadState.Loading) }
    var detailState by remember { mutableStateOf<LoadState<PrayerDetail>>(LoadState.Loading) }
    var retry by remember { mutableIntStateOf(0) }
    var reviewedCatalog by remember { mutableStateOf<PrayerCatalog?>(null) }
    var loadedRetry by remember { mutableIntStateOf(-1) }
    val labels=prayerCatalogTexts(language)
    val uriHandler=LocalUriHandler.current

    LaunchedEffect(textLanguage, retry) {
        if(reviewedCatalog!=null&&loadedRetry==retry){listState=LoadState.Ready(reviewedCatalog!!);return@LaunchedEffect}
        listState=LoadState.Loading
        try {
            // UI languages are not automatically source editions. The compatible query
            // discovers the reviewed catalogue without sending unsupported UK/EN requests.
            val catalog=client.getPrayerCatalog("ru").validatePrayerCatalog()
            if(catalog.catalogVersion==2){reviewedCatalog=catalog;loadedRetry=retry}
            listState=LoadState.Ready(if(catalog.catalogVersion==2)catalog else {
                val legacyLanguage=if(textLanguage=="all")language else textLanguage
                if(legacyLanguage=="ru")catalog else client.getPrayerCatalog(legacyLanguage).validatePrayerCatalog()
            })
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){listState=LoadState.Error}
    }
    LaunchedEffect(selectedPrayer, selectedEdition, retry) {
        val id=selectedPrayer?:return@LaunchedEffect
        detailState=LoadState.Loading
        try {
            val detail=(selectedEdition?.let{client.getPrayer(id,it)}?:client.getPrayer(id)).validatePrayerDetail()
            require(selectedEdition==null||detail.languageCode==selectedEdition)
            detailState=LoadState.Ready(detail)
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){detailState=LoadState.Error}
    }
    BackHandler {if(selectedPrayer!=null)selectedPrayer=null else onBack()}
    if(selectedPrayer==null){
        ContentListPage(localText(R.string.prayers_title,language),language,onBack){
            val catalog=(listState as? LoadState.Ready)?.value
            val reviewed=catalog?.catalogVersion==2
            item {
                Text(localText(R.string.prayer_text_language,language),color=Ink,fontWeight=FontWeight.Bold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    val languages=if(reviewed) linkedSetOf("all").apply {
                        catalog!!.data.forEach{addAll(it.availableLanguages)}
                        catalog.externalSources.forEach{add(it.language)}
                    } else linkedSetOf("all").apply{addAll(com.bibledesktop.shared.presentation.interfaceLanguages.keys);add("cu");add("cu-civil")}
                    languages.forEach{code->
                        val label=when(code){"all"->labels.all;"cu"->localText(R.string.prayer_cu,language);"cu-civil"->localText(R.string.prayer_cu_civil,language);else->com.bibledesktop.shared.presentation.interfaceLanguages[code]?:code}
                        FilterChip(textLanguage==code,onClick={textLanguage=code},modifier=Modifier.testTag("prayer-edition-$code"),label={Text(label)})
                    }
                }
                if(reviewed)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    (listOf("all")+catalog!!.groups.keys).forEach{key->FilterChip(group==key,onClick={group=key},modifier=Modifier.testTag("prayer-group-$key"),label={Text(if(key=="all")labels.all else labels.groups[key]?:catalog.groups.getValue(key))})}
                }
            }
            when(val current=listState){
                LoadState.Loading->item{LoadingBox()}
                LoadState.Error->item{ErrorBox(localText(R.string.prayers_error,language),localText(R.string.retry,language),{retry+=1})}
                is LoadState.Ready->{
                    val value=current.value
                    val entries=if(value.catalogVersion==2)value.data.filter{it.catalogVisible==true&&(group=="all"||group in it.groups)&&(textLanguage=="all"||textLanguage in it.availableLanguages)}.distinctBy{it.canonicalSlug} else value.data
                    item {Text(localText(R.string.prayer_source_count,language,entries.size),Modifier.testTag("prayer-source-count").padding(vertical=8.dp),color=PrimaryBlue)}
                    if(entries.isEmpty())item{EmptyBox(localText(R.string.prayers_empty_language,language))}
                    items(entries,key={it.canonicalSlug?:it.id.toString()}){prayer->
                        val edition=if(value.catalogVersion==2&&textLanguage!="all")textLanguage else prayer.languageCode
                        PrayerCard(prayer,edition){selectedEdition=if(value.catalogVersion==2)edition else null;selectedPrayer=prayer.id}
                    }
                    val external=value.externalSources.filter{textLanguage=="all"||it.language==textLanguage}
                    if(external.isNotEmpty()){
                        item{Text(labels.external,color=Ink,fontWeight=FontWeight.Bold);Text(labels.externalOnly,Modifier.testTag("prayer-external-only"),color=PrimaryBlue)}
                        items(external,key={it.url}){source->Button(onClick={uriHandler.openUri(source.url)},modifier=Modifier.fillMaxWidth()){Text(source.title)}}
                    }
                }
            }
        }
    }else PrayerReader(language,detailState,{selectedPrayer=null},onBack,{retry+=1})
}

@Composable
private fun PrayerCard(prayer: PrayerSummary, edition:String=prayer.languageCode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("prayer-card-${prayer.canonicalSlug?:prayer.id}").padding(bottom = 10.dp).clickable(onClick = onClick),
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
                    prayer.excerpt.orEmpty(),
                    color = PrimaryBlue,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            Text(edition.uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    PrayerReadingContent(current.value, fontSize, language)
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
                        share(context, "${prayer.title}\n\n${prayer.plainText ?: readingText(prayer.body)}")
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
            modifier = Modifier.weight(1f).testTag("daily-content-list"),
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
