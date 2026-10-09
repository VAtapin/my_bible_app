package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.TranslationSummary

/** Exact code/name matches from Glottolog CLDF (CC BY 4.0); unknown or ambiguous names stay Other.
 * https://github.com/glottolog/glottolog-cldf/blob/master/cldf/languages.csv
 * Slavic/Germanic/Romance are convenient subgroups of Indo-European, not inferred from script.
 */
private val languageGroups = buildMap<String, String> {
    fun group(family: String, codes: String) { codes.split(' ').forEach { put(it, family) } }
    group("slavic", "ru uk cu cu-civil pl bg cs sk sr hr sl be mk")
    group("germanic", "de en nl da sv no is")
    group("romance", "la fr es pt it ro")
    group("indo", "bagheli dari dhundari dogri gaddi garhwali haryanvi mb1radf4 mb5307i9 mb6qm338 mba95m3g")
    group("atlantic", "abron abua aghem akoose alladian awing baatonum babanki bakoko beembe berom bokyi bukusu bum chokwe chopi dagbani denya dii doyayo duruma ebira eggon ejagham esimbi eten farefare fipa ga gbagyi gen gitonga gogo gun hehe mb9fxacy mbabk951 mbcboxkr")
    group("nilotic", "adhola alur anuak bari")
    group("sino", "aimol akha anal apatani biete dimasa galo gangte hmar mb1w2vsx mb2nwr95 mb3v5den mb5n0x8u mba6bfxf mbardkid mbc3xglo")
    group("austronesian", "balangao balantak banggai biak bima bunama halia hano mb37iyht mb38xvel mb529y9r mb52q8j2 mb55zqzu mb5a9tyl mb5qwnux mb5z4yd7 mb62lic7 mb842v7a mb94p1bm mbav6ql8 mbcl01zu")
    group("afro", "am cuvok gidar hadiyya mb2hquf9 mb9d1635 mbcjcqgd")
    group("austroasiatic", "bahnar mb1h3kid")
    group("mande", "bandi")
    group("kru", "bassa")
    group("papuan", "benabena citak ekari fore golin mb1vol4k mb382fhv mb4x6sp0 mb64tekf mb9a1k8m")
    group("otomanguean", "mb1uwwiq mba168dp mbbwwsft mbcafqcy mboiybl2")
    group("mayan", "chol chuj")
    group("arawakan", "garifuna")
    group("dravidian", "mb7y7274")
}

internal fun translationGroup(translation: TranslationSummary): String = languageGroups[translation.language.code]
    ?: catalogLanguageFamilies[translation.language.code] ?: "other"

internal fun groupTitleId(group: String): Int = when (group) {
    "slavic" -> R.string.group_slavic
    "germanic" -> R.string.group_germanic
    "romance" -> R.string.group_romance
    "indo" -> R.string.group_indo
    "atlantic" -> R.string.group_atlantic
    "nilotic" -> R.string.group_nilotic
    "sino" -> R.string.group_sino
    "austronesian" -> R.string.group_austronesian
    "afro" -> R.string.group_afro
    "austroasiatic" -> R.string.group_austroasiatic
    "mande" -> R.string.group_mande
    "kru" -> R.string.group_kru
    "papuan" -> R.string.group_papuan
    "otomanguean" -> R.string.group_otomanguean
    "mayan" -> R.string.group_mayan
    "arawakan" -> R.string.group_arawakan
    "dravidian" -> R.string.group_dravidian
    "other" -> R.string.group_other
    else -> R.string.language_all
}

internal fun matchingTranslations(catalog: List<TranslationSummary>, query: String, group: String, code: String): List<TranslationSummary> {
    val tokens = bookSearchKey(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return catalog.filter {
        (group.isEmpty() || translationGroup(it) == group) && (code.isEmpty() || it.language.code == code) &&
            tokens.all(bookSearchKey(listOfNotNull(it.name, it.shortName, it.code, it.language.code, it.language.name, it.language.nativeName).joinToString(" "))::contains)
    }.sortedWith(compareBy({ bookSearchKey(it.language.nativeName ?: it.language.name) }, { bookSearchKey(it.name) }))
}

/** Searchable pickers avoid hundreds of chips and keep new API languages discoverable. */
@Composable
internal fun CatalogFilters(language: String, catalog: List<TranslationSummary>, query: String, onQuery: (String) -> Unit,
    group: String, onGroup: (String) -> Unit, code: String, onCode: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().testTag("translation-search"),
            label = { Text(localized(R.string.translation_search, language)) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { choosing = "group" }, modifier = Modifier.weight(1f).testTag("catalog-group")) {
                Text(localized(R.string.catalog_group, language) + ": " + localized(groupTitleId(group), language))
            }
            OutlinedButton(onClick = { choosing = "language" }, modifier = Modifier.weight(1f).testTag("catalog-language")) {
                Text(localized(R.string.catalog_language, language) + ": " + (catalog.firstOrNull { it.language.code == code }?.let { it.language.nativeName ?: it.language.name }
                    ?: localized(R.string.language_all, language)))
            }
        }
    }
    if (choosing.isNotEmpty()) {
        val options = if (choosing == "group") catalog.map(::translationGroup).distinct().map { it to localized(groupTitleId(it), language) }.sortedBy { it.second }
            else catalog.filter { group.isEmpty() || translationGroup(it) == group }.distinctBy { it.language.code }
                .map { it.language.code to (it.language.nativeName ?: it.language.name) }.sortedBy { bookSearchKey(it.second) }
        FilterChoice(language, options, onClose = { choosing = "" }) {
            if (choosing == "group") { onGroup(it); onCode("") } else onCode(it)
            choosing = ""
        }
    }
}

@Composable
private fun FilterChoice(language: String, options: List<Pair<String, String>>, onClose: () -> Unit, onSelect: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(localized(R.string.catalog_filter, language)) },
        confirmButton = { TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) } },
        text = {
            Column {
                OutlinedTextField(search, onValueChange = { search = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("filter-search"))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { TextButton(onClick = { onSelect("") }, modifier = Modifier.fillMaxWidth()) { Text(localized(R.string.language_all, language)) } }
                    items(options.filter { bookSearchKey(it.second + " " + it.first).contains(bookSearchKey(search)) }, key = { it.first }) {
                        TextButton(onClick = { onSelect(it.first) }, modifier = Modifier.fillMaxWidth().testTag("filter-${it.first}")) { Text(it.second) }
                    }
                }
            }
        })
}
