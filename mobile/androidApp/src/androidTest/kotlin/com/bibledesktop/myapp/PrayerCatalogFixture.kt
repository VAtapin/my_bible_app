package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.shared.api.*
import kotlinx.serialization.json.*

/** Public reviewed backend blocks; numeric IDs are deliberately fixture-local. */
internal class PrayerCatalogFixture {
    private val json=Json{ignoreUnknownKeys=true}
    private val raw=json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("prayer-catalog-reviewed.json").bufferedReader().use{it.readText()}).jsonObject
    val catalog=json.decodeFromJsonElement(PrayerCatalog.serializer(),raw.getValue("catalog"))
    val details=raw.getValue("details").jsonObject.mapKeys{it.key.toLong()}.mapValues{json.decodeFromJsonElement(PrayerDetail.serializer(),it.value)}
    fun proof(slug:String,key:String)=raw.getValue("proof").jsonObject.getValue(slug).jsonObject.getValue(key).jsonPrimitive.content
}
