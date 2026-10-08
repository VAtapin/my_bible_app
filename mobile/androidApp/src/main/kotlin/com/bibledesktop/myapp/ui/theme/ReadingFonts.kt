package com.bibledesktop.myapp.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.bibledesktop.myapp.R

private val ponomar = FontFamily(Font(R.font.ponomar))
private val monomakh = FontFamily(Font(R.font.monomakh))

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resource: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

val InterfaceFont = variableFamily(R.font.inter)
val ReadingSerif = variableFamily(R.font.noto_serif)

/** The edition belongs to the text, not to the interface language. */
fun readingFont(languageCode: String): FontFamily = when (languageCode) {
    "cu" -> ponomar
    "cu-civil" -> monomakh
    else -> ReadingSerif
}
