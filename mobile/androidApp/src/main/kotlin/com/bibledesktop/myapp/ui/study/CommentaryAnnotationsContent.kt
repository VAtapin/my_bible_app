package com.bibledesktop.myapp.ui.study
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI

@Composable internal fun CommentaryAnnotationsContent(language:String,annotations:CommentaryAnnotations?,client:BibleContentSource,translationCode:String?=null,moduleCode:String?=null){
 if(annotations==null||annotations.links.isEmpty()&&annotations.media.isEmpty())return
 val context=LocalContext.current;val uris=LocalUriHandler.current;val scope=rememberCoroutineScope()
 val labels=when(language){"de"->listOf("Quellenlinks","Abbildungen der Quelle","Material nicht zugeordnet","Die Verszählung des Quellenlinks ist nicht bestätigt.");"uk"->listOf("Посилання джерела","Ілюстрації джерела","Матеріал не зіставлено","Нумерацію вихідного посилання не підтверджено.");"en"->listOf("Source links","Source illustrations","Material unresolved","The source link’s verse numbering is unconfirmed.");else->listOf("Ссылки источника","Иллюстрации источника","Материал не сопоставлен","Нумерация исходной ссылки не подтверждена.")}
 val mediaRepository=remember(context){com.bibledesktop.myapp.data.CommentaryMediaRepository(context)}
 var images by remember(annotations,moduleCode){mutableStateOf(emptyMap<Int,java.io.File>())}
 var loadingImages by remember(annotations,moduleCode){mutableStateOf(moduleCode!=null&&annotations.media.any{it.status=="resolved"})}
 LaunchedEffect(annotations,moduleCode){images=emptyMap();loadingImages=moduleCode!=null;try{if(moduleCode!=null)annotations.media.forEachIndexed{index,media->if(media.status=="resolved")try{images=images+(index to mediaRepository.load(moduleCode,media))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}}}finally{loadingImages=false}}
 var passage by remember{mutableStateOf<ResolvedStudyReference?>(null)};var failed by remember{mutableStateOf(false)}
 Column{
  if(annotations.links.isNotEmpty())Text(labels[0],style=MaterialTheme.typography.titleSmall)
  if(annotations.links.any{it.kind=="bible"})Text(labels[3])
  annotations.links.forEach{link->
   val target=if(link.kind=="bible"&&link.bookSlug!=null&&(link.chapter?:0)>0&&(link.verse?:0)>0)DictionaryReference(requireNotNull(link.bookSlug),link.chapter,link.verse,link.verse)else null
   val external=if(link.kind=="external")runCatching{URI(link.href).takeIf{it.scheme.equals("https",true)&&!it.host.isNullOrBlank()&&it.userInfo==null}}.getOrNull()else null
   val label=studySourceName(link.label).ifBlank{link.href}
   when{target!=null->TextButton(onClick={scope.launch{failed=false;try{val code=translationCode?:context.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE).getString("lastTranslation","").orEmpty();passage=resolveStudyReference(client,code,target)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}}}){Text(label)}
    external!=null->TextButton(onClick={runCatching{uris.openUri(external.toString())}.onFailure{failed=true}}){Text("$label · ${external.host}")}
    else->Text("$label · ${labels[2]}")}
  }
  if(annotations.media.isNotEmpty())Text(labels[1],style=MaterialTheme.typography.titleSmall)
  annotations.media.forEachIndexed{index,media->val file=images[index];if(file!=null)AtlasImage(file,media.alt?:media.src.orEmpty(),language)else if(media.status=="resolved"){if(loadingImages)LinearProgressIndicator()else Text(dictionaryTexts(language).error)}else Text("${studySourceName(media.alt.orEmpty())} ${media.src?:media.fragmentId.orEmpty()} · ${labels[2]}")}
  if(failed)Text(studyTexts(language).missing)
 }
 passage?.let{TemporaryStudyPassage(language,it.code,it.targets,client){passage=null}}
}
