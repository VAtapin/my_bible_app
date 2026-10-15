package com.bibledesktop.myapp.ui.daily

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class BookTexts(val books:String,val contents:String,val edition:String,val pages:String,val source:String,val loading:String,val unavailable:String)
internal fun bookTexts(language:String)=when(language){
    "de"->BookTexts("Gebetbücher","Inhalt","Ausgabe","Seiten","Quelle","Wird geladen…","Text nicht verfügbar")
    "uk"->BookTexts("Молитовники (книги)","Зміст","Видання","Сторінки","Джерело","Завантаження…","Текст недоступний")
    "en"->BookTexts("Prayer books","Contents","Edition","Pages","Source","Loading…","Text unavailable")
    else->BookTexts("Молитвословы (книги)","Оглавление","Издание","Страницы","Источник","Загрузка…","Текст недоступен")
}

private fun pageLabel(pages:List<Int>)=if(pages.size>1&&pages.zipWithNext().all{(a,b)->b==a+1})"${pages.first()}–${pages.last()}" else pages.joinToString(", ")

@Composable
internal fun LiturgicalBookLibrary(language:String,client:BibleContentSource,onBack:()->Unit){
    val labels=bookTexts(language)
    var works by remember { mutableStateOf<List<LiturgicalWorkSummary>?>(null) }
    var error by remember { mutableStateOf(false) }
    var slug by rememberSaveable { mutableStateOf<String?>(null) }
    var actualLanguage by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val list=rememberLazyListState()
    LaunchedEffect(Unit){try{works=client.getLiturgicalWorks("prayer-books")}catch(c:CancellationException){throw c}catch(_:Exception){error=true}}
    val selected=slug
    if(selected!=null){LiturgicalReader(language,client,selected,actualLanguage,{slug=null},onBack);return}
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        ReadingHeader(labels.books,language,onBack,onBack)
        LazyColumn(state=list,modifier=Modifier.weight(1f),contentPadding=PaddingValues(12.dp)){
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                (listOf("all")+works.orEmpty().flatMap{it.availableLanguages}.distinct()).forEach{code->
                    FilterChip(filter==code,onClick={filter=code},label={Text(if(code=="all")prayerCatalogTexts(language).all else com.bibledesktop.shared.presentation.interfaceLanguages[code]?:code)})
                }
            }}
            if(works==null)item{Text(if(error)labels.unavailable else labels.loading)}
            items(works.orEmpty().filter{filter=="all"||filter in it.availableLanguages},key={it.slug}){work->
                ListItem(headlineContent={Text(work.title)},supportingContent={Text(work.availableLanguages.joinToString(" · "))},modifier=Modifier.clickable{
                    actualLanguage=if(filter!="all")filter else if(language in work.availableLanguages)language else work.availableLanguages.first()
                    slug=work.slug
                })
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun LiturgicalReader(language:String,client:BibleContentSource,initialSlug:String,actualLanguage:String,onBack:()->Unit,onHome:()->Unit,initialEdition:String?=null){
    val labels=bookTexts(language)
    var slug by rememberSaveable(initialSlug) { mutableStateOf(initialSlug) }
    var edition by rememberSaveable(initialSlug,actualLanguage) { mutableStateOf<String?>(initialEdition) }
    var summary by remember { mutableStateOf<LiturgicalWorkSummary?>(null) }
    var version by remember { mutableStateOf<LiturgicalWorkVersion?>(null) }
    var error by remember { mutableStateOf(false) }
    var contents by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var units by remember { mutableStateOf<List<LiturgicalWorkSummary>>(emptyList()) }
    var fontSize by rememberSaveable { mutableFloatStateOf(19f) }
    var menu by remember { mutableStateOf(false) }
    var bookPosition by rememberSaveable { mutableIntStateOf(0) }
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(slug,edition){
        version=null;summary=null;error=false
        try{
            val meta=client.getLiturgicalWork(slug);summary=meta
            val selected=edition?:meta.editions.firstOrNull{it.language==actualLanguage}?.code
            require(selected!=null)
            version=client.getLiturgicalVersion(slug,actualLanguage,selected)
            list.scrollToItem(if(slug==initialSlug)bookPosition else 0)
        }catch(c:CancellationException){throw c}catch(_:Exception){error=true}
    }
    LaunchedEffect(section,version?.edition){
        units=emptyList()
        val current=version?.bookSections?.find{it.id==section}?:return@LaunchedEffect
        units=current.works.mapNotNull { ref -> try{client.getLiturgicalWork(ref.slug)}catch(c:CancellationException){throw c}catch(_:Exception){null} }
    }
    fun back(){when{contents->contents=false;slug!=initialSlug->{slug=initialSlug;edition=null};else->onBack()}}
    BackHandler{back()}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        ReadingHeader(version?.title?:labels.loading,language,{back()},onHome)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            if(version?.bookSections?.isNotEmpty()==true)TextButton(onClick={contents=true}){Text(labels.contents)}
            val choices=summary?.editions?.filter{it.language==actualLanguage}.orEmpty()
            if(choices.size>1)Box{
                TextButton(onClick={menu=true}){Text(labels.edition)}
                DropdownMenu(menu,onDismissRequest={menu=false}){choices.forEach{choice->DropdownMenuItem(text={Text(choice.title)},onClick={menu=false;edition=choice.code})}}
            }
            TextButton(onClick={fontSize=(fontSize-1).coerceAtLeast(15f)}){Text("A−")}
            TextButton(onClick={fontSize=(fontSize+1).coerceAtMost(28f)}){Text("A+")}
        }
        val value=version
        if(value==null)Text(if(error)labels.unavailable else labels.loading,Modifier.padding(12.dp))
        else LazyColumn(state=list,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)){
            item{Text(value.editionTitle,fontSize=12.sp)}
            summary?.intro?.takeIf{it.isNotBlank()}?.let{intro->item{Text(readingText(intro),fontSize=14.sp)}}
            items(value.blocks,key={it.id}){block->
                Text(block.text,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),fontFamily=readingFont(value.language),fontSize=fontSize.sp,lineHeight=(fontSize*1.5f).sp,fontWeight=if(block.kind=="heading")FontWeight.Bold else FontWeight.Normal,color=if(block.kind=="rubric")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            items(value.sourceEditionNotes){Text(it,fontSize=14.sp)}
        }
        if(contents&&value!=null)AlertDialog(onDismissRequest={contents=false},confirmButton={},title={Text(labels.contents)},text={
            LazyColumn{
                items(value.bookSections,key={it.id}){part->
                    TextButton(onClick={
                        val index=value.blocks.indexOfFirst{it.id==part.startBlockId}
                        if(index>=0){contents=false;scope.launch{list.scrollToItem(index+1+(if(summary?.intro.isNullOrBlank())0 else 1))}}
                        else section=if(section==part.id)null else part.id
                    }){Text(part.title)}
                    if(part.printedPages.isNotEmpty())Text("${labels.pages}: ${pageLabel(part.printedPages)}",fontSize=12.sp)
                    if(section==part.id)units.filter{actualLanguage in it.availableLanguages}.forEach{unit->
                        TextButton(onClick={bookPosition=list.firstVisibleItemIndex;slug=unit.slug;edition=unit.editions.firstOrNull{it.code==value.edition&&it.language==actualLanguage}?.code?:unit.editions.first{it.language==actualLanguage}.code;section=null;contents=false}){Text(unit.title)}
                    }
                }
            }
        })
    }
}
