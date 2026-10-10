package com.bibledesktop.myapp.ui.study
import android.content.SharedPreferences
import com.bibledesktop.shared.api.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal fun readCommentarySourceRules(preferences:SharedPreferences):List<CommentarySourceRule> = runCatching{
 Json.decodeFromString(ListSerializer(CommentarySourceRule.serializer()),preferences.getString("sources-ranges","[]")!!).filter{it.book.isNotBlank()&&it.first.chapter>0&&it.first.verse>0&&it.last.chapter>0&&it.last.verse in 1 until Int.MAX_VALUE&&it.first<=it.last&&it.sources.size<=30}
}.getOrDefault(emptyList())
internal fun writeCommentarySourceRules(preferences:SharedPreferences,rules:List<CommentarySourceRule>){preferences.edit().putString("sources-ranges",Json.encodeToString(ListSerializer(CommentarySourceRule.serializer()),rules)).apply()}
