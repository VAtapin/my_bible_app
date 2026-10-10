package com.bibledesktop.myapp.data

import com.bibledesktop.shared.api.*
import java.io.File

/** Existing online/installed dictionary reader boundary, shared by dictionaries and atlas UI. */
internal interface DictionaryReadingSource:AutoCloseable {
    suspend fun modules():List<DictionaryModule>
    suspend fun downloadedModules():List<DictionaryModule>
    suspend fun searchInstalled(query:String,codes:List<String>,offset:Int=0,limit:Int=50):DictionaryPage
    suspend fun entries(module:String,query:String,offset:Int):DictionaryPage
    suspend fun article(module:DictionaryModule,key:String):DictionaryArticle
    suspend fun lookup(query:String,module:String):List<DictionaryWordForm>
    suspend fun image(module:String,media:DictionaryMedia,version:String?=null):File
}
