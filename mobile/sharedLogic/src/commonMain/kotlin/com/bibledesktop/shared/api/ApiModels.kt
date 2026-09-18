package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiEnvelope<T>(
    val data: T,
)

@Serializable
data class LanguageSummary(
    val code: String,
    val name: String,
    @SerialName("native_name") val nativeName: String? = null,
)

@Serializable
data class TranslationSummary(
    val code: String,
    val name: String,
    @SerialName("short_name") val shortName: String? = null,
    val language: LanguageSummary,
    @SerialName("canon_code") val canonCode: String? = null,
    @SerialName("has_old_testament") val hasOldTestament: Boolean = true,
    @SerialName("has_new_testament") val hasNewTestament: Boolean = true,
    @SerialName("has_apocrypha") val hasApocrypha: Boolean = false,
    @SerialName("has_strong") val hasStrong: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
)
