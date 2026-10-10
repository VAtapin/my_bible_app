package com.bibledesktop.myapp.ui.bible

/** TextField selection indices refer to the exact original verse string. */
internal fun dictionarySelectionQuery(text:String,start:Int,end:Int):String? {
    val from=minOf(start,end);val to=maxOf(start,end)
    if(from<0||to>text.length||from>=to)return null
    // Never split a surrogate pair into a malformed request.
    if(from>0&&text[from].isLowSurrogate()&&text[from-1].isHighSurrogate())return null
    if(to<text.length&&text[to].isLowSurrogate()&&text[to-1].isHighSurrogate())return null
    val query=text.substring(from,to).trim()
    return query.takeIf{it.isNotEmpty()&&it.length<=120&&it.none{char->char.isISOControl()&&!char.isWhitespace()}}
}
