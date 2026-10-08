package com.bibledesktop.myapp.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bibledesktop.myapp.R

private val ponomar = FontFamily(Font(R.font.ponomar))
private val monomakh = FontFamily(Font(R.font.monomakh))

/** The edition belongs to the text, not to the interface language. */
fun readingFont(languageCode: String): FontFamily = when (languageCode) {
    "cu" -> ponomar
    "cu-civil" -> monomakh
    else -> FontFamily.Serif
}
