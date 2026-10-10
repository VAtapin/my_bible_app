package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun WordMarkNoteDialog(language:String,mark:WordMark,onClose:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope();var editing by remember(mark.id){mutableStateOf(false)};var draft by remember(mark.id){mutableStateOf(mark.note)};var failed by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 fun t(key:String)=personalStudyText(language,key)
 AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(t("note"))},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){Text("${mark.code} · ${mark.osis}");Text(mark.quote);if(editing)OutlinedTextField(draft,{draft=it},modifier=Modifier.testTag("word-note-editor"),label={Text(t("note"))})else Text(mark.note);if(failed)Text(t("failed"))}},
  confirmButton={TextButton(enabled=!busy,onClick={if(!editing)editing=true else{busy=true;scope.launch{try{PersonalStudyStore.update(context){updateWordNote(it,mark,draft)};onClose()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}finally{busy=false}}}},modifier=Modifier.testTag("word-note-save")){Text(t(if(editing)"save"else"edit"))}},
  dismissButton={TextButton(enabled=!busy,onClick=onClose){Text(t("close"))}})
}
