package com.bibledesktop.myapp.ui.bible

import android.content.Context
import com.bibledesktop.shared.api.PersonalStudy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Personal records share the existing profile; invalid data is never replaced by an empty document. */
object PersonalStudyStore {
    private const val Key = "personalStudyV1"
    private val writes = Mutex()
    val changes = MutableStateFlow(0L)
    private fun preferences(context: Context) = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    private fun load(context: Context): PersonalStudy = preferences(context).getString(Key, null)?.let { Json.decodeFromString<PersonalStudy>(it).also { value -> check(value.version == 1) } } ?: PersonalStudy()
    suspend fun read(context: Context): PersonalStudy = withContext(Dispatchers.IO) { load(context) }
    suspend fun update(context: Context, transform: (PersonalStudy) -> PersonalStudy): PersonalStudy = withContext(Dispatchers.IO) {
        writes.withLock {
            val result = transform(load(context)); check(result.version == 1)
            check(preferences(context).edit().putString(Key, Json.encodeToString(result)).commit())
            changes.value += 1
            result
        }
    }
}
