package com.bibledesktop.shared.api

/** Explicit source identifiers only; never derive a word relation from token_order. */
fun sourceStrongNumbers(raw: String, hasMarkup: Boolean): List<String> {
    if (!hasMarkup) return emptyList()
    val safe = raw.replace(Regex("<(script|style|iframe|object)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE), "").replace(Regex("<[^>]*>"), " ")
    return Regex("(?<![\\p{L}\\p{N}])([HG])(\\d{1,5})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).findAll(safe)
        .map { it.groupValues[1].uppercase() + it.groupValues[2].toInt() }.filter { it.substring(1).toInt() > 0 }.distinct().toList()
}
