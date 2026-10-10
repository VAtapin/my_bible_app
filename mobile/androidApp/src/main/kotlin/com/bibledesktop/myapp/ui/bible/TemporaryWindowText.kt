package com.bibledesktop.myapp.ui.bible

internal data class TemporaryWindowTexts(val separate:String,val window:String,val back:String,val temporary:String)
internal fun temporaryWindowTexts(language:String)=when(language){
 "ru"->TemporaryWindowTexts("Отдельное окно","Окно","Вернуться к чтению","Временное чтение · синхронизация отключена")
 "uk"->TemporaryWindowTexts("Окреме вікно","Вікно","Повернутися до читання","Тимчасове читання · синхронізацію вимкнено")
 "de"->TemporaryWindowTexts("Separates Fenster","Fenster","Zur Lesestelle zurück","Vorübergehendes Lesen · Synchronisation aus")
 else->TemporaryWindowTexts("Separate window","Window","Return to reading","Temporary reading · synchronization off")
}
