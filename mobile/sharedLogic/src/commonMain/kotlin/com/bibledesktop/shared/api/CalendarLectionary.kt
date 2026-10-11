package com.bibledesktop.shared.api

import kotlinx.serialization.Serializable

@Serializable data class CalendarLectionaryCandidate(val book:String,val number:Int)
@Serializable data class CalendarLectionary(val status:String,val candidates:List<CalendarLectionaryCandidate> = emptyList())
fun CalendarReading.lectionaryNumber():Int? {
    val value=reading?.lectionary?:return null
    if(value.status!="exact-reference-match"||value.candidates.isEmpty()||value.candidates.any{it.book.isBlank()||it.number<=0})return null
    return value.candidates.distinctBy{it.book to it.number}.singleOrNull()?.number
}
