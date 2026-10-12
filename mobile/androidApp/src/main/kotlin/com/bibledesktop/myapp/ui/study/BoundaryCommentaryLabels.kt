package com.bibledesktop.myapp.ui.study

internal data class BoundaryCommentaryLabels(val materials:String,val book:String,val chapter:String,val before:String,val after:String)
internal fun boundaryCommentaryLabels(language:String)=when(language){
 "de"->BoundaryCommentaryLabels("Materialien","Zum Buch","Zum Kapitel","Vor dem Text","Nach dem Text")
 "uk"->BoundaryCommentaryLabels("Матеріали","До книги","До глави","Перед текстом","Після тексту")
 "en"->BoundaryCommentaryLabels("Materials","About the book","About chapter","Before the text","After the text")
 else->BoundaryCommentaryLabels("Материалы","К книге","К главе","Перед текстом","После текста")
}
