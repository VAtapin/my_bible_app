package com.bibledesktop.myapp.data

import kotlinx.serialization.json.*
import java.net.URLEncoder

/** Append only our published revision after DictionaryApi has validated the source URL. */
internal fun dictionaryImageRevisionUrl(validatedUrl:String,revision:String?):String =
    if(revision.isNullOrBlank())validatedUrl else "$validatedUrl?v=${URLEncoder.encode(revision,"UTF-8")}"

/** New media revisions supersede archive-only packages; legacy matching hashes remain readable. */
internal fun dictionaryPackageRevisionMatches(metadata:JsonObject,published:String?):Boolean {
    val installed=metadata["content_version"]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank()}
        ?: metadata["source_archive_sha256"]?.jsonPrimitive?.contentOrNull
    return published==null || installed==published
}
