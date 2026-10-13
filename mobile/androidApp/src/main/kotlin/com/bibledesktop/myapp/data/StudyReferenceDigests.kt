package com.bibledesktop.myapp.data

import kotlinx.serialization.json.*
import java.security.MessageDigest

internal fun studyTextDigest(text:String):String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}

/** Public source edges only. Retain duplicates and nulls; neither Unicode nor source text is rewritten. */
internal fun studySourceDigest(code:String,osis:String,rows:List<JsonObject>):String {
    val edges=rows.map { row ->
        require(row["source_code"]?.jsonPrimitive?.content==code && row["source_verse"]?.jsonObject?.get("osis_ref")?.jsonPrimitive?.content==osis)
        val metadata=row["metadata"] as? JsonObject
        val quote=metadata?.get("legacy_quote_id")
        require(quote==null || quote==JsonNull || (quote is JsonPrimitive && !quote.isString && quote.longOrNull!=null))
        JsonArray(listOf(
            JsonPrimitive(row.getValue("target_verse").jsonObject.getValue("osis_ref").jsonPrimitive.content),
            row["type"] ?: JsonNull,
            metadata?.get("raw_ref") ?: JsonNull,
            quote ?: JsonNull,
        ))
    }.sortedWith { a,b ->
        val first=a.toString().toByteArray(Charsets.UTF_8);val second=b.toString().toByteArray(Charsets.UTF_8)
        var value=0
        for(index in 0 until minOf(first.size,second.size)) {
            value=(first[index].toInt() and 255)-(second[index].toInt() and 255)
            if(value!=0)break
        }
        if(value==0)first.size-second.size else value
    }
    return studyTextDigest(JsonArray(listOf(JsonPrimitive(code),JsonPrimitive(osis),JsonArray(edges))).toString())
}
