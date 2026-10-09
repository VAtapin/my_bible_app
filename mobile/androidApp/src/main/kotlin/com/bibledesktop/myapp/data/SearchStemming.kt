package com.bibledesktop.myapp.data
import org.tartarus.snowball.SnowballStemmer
import org.tartarus.snowball.ext.russianStemmer
import org.tartarus.snowball.ext.germanStemmer
import org.tartarus.snowball.ext.englishStemmer
import java.text.Normalizer
import java.util.Locale
internal val stemmingLanguages = setOf("ru","rus","de","deu","ger","en","eng")
/** Snowball stems conflate regular inflections; they do not infer dictionary meanings. */
internal class SearchStemming(language:String) {
    private val stemmer:SnowballStemmer = when(language) {
        "ru","rus" -> russianStemmer()
        "de","deu","ger" -> germanStemmer()
        "en","eng" -> englishStemmer()
        else -> error("Stemming is unavailable for this language")
    }
    fun word(value:String):String {
        stemmer.current=Normalizer.normalize(value,Normalizer.Form.NFC).lowercase(Locale.ROOT).replace('ё','е')
        stemmer.stem()
        return stemmer.current
    }
    fun tokens(text:String)=Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(text).map{word(it.value)}.filter(String::isNotBlank).toList()
}
