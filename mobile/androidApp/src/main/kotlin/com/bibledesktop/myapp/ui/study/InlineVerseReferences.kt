package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.shared.api.*
import com.bibledesktop.myapp.data.OfflineContentRepository

internal val LocalVerseStudyClient=staticCompositionLocalOf<BibleContentSource?>{null}
/** Optional compact textual links. No counters, errors or commentary rows in reading text. */
@Composable internal fun InlineVerseReferences(language:String,chapter:BibleChapter,verse:BibleVerse,onStudy:()->Unit){
 val client=LocalVerseStudyClient.current?:return
 val context=LocalContext.current;val loader=LocalInlineReferenceLoader.current;val view=LocalView.current
 val store=remember(context){ReferenceDisplayStore(context)}
 val revision by ReferenceDisplayStore.changes.collectAsState()
 val settings=remember(revision){store.load()}
 if(!settings.list)return
 var visible by remember(client,verse.id,verse.osisRef,chapter.translation.code){mutableStateOf(false)}
 var groups by remember(client,verse.id,verse.osisRef,chapter.translation.code,settings.inlineSources){mutableStateOf(emptyList<ReferenceGroup>())}
 var temporary by remember{mutableStateOf<List<ReferenceTarget>?>(null)}
 LaunchedEffect(client,loader,visible,verse.id,verse.osisRef,chapter.translation.code,settings.inlineSources){
  if(!visible)return@LaunchedEffect
  groups=emptyList()
  groups=loader.load(attempt={
   val data=((client as? OfflineContentRepository)?.getCrossReferencesAt(verse.id,chapter.translation.code,verse.osisRef)?:client.getCrossReferences(verse.id,chapter.translation.code))
    .also{require(it.verse.osisRef==verse.osisRef&&it.translationCode==chapter.translation.code)}
   val candidates=displayReferenceGroups(data.references,settings.inlineSources)
   val actual=resolveReferenceCardText(client,chapter.translation.code,candidates.flatMap{it.targets})
   candidates.filter{referenceGroupAvailable(it,actual)}
  })
 }
 Column(Modifier.fillMaxWidth().heightIn(min=1.dp).onGloballyPositioned{
  val bounds=it.boundsInWindow();visible=bounds.height>0&&bounds.bottom>0&&bounds.top<view.height
 }){
  if(groups.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
   groups.forEach{group->Text(group.label,fontSize=12.sp,lineHeight=16.sp,modifier=Modifier
    .testTag("inline-reference-${group.targets.first().osisRef}")
    .clickable(role=Role.Button){temporary=group.targets})}
  }
 }
 temporary?.let{TemporaryStudyPassage(language,chapter.translation.code,it,client){temporary=null}}
}
