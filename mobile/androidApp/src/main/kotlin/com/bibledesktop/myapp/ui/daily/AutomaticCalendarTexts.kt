package com.bibledesktop.myapp.ui.daily

import androidx.compose.runtime.Composable
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.AutomaticCalendarText
import java.util.Locale

@Composable internal fun automaticCalendarEditionLabel(text:AutomaticCalendarText,uiLanguage:String):String{
    val language=when(text.language){
        "cu"->localized(R.string.prayer_cu,uiLanguage)
        "cu-civil"->localized(R.string.prayer_cu_civil,uiLanguage)
        null->null
        else->Locale.forLanguageTag(text.language).getDisplayLanguage(Locale.forLanguageTag(uiLanguage))
    }
    return listOfNotNull(language,text.edition).joinToString(" · ")
}
