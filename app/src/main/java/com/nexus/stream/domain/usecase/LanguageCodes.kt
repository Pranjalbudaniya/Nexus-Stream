package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.StreamLanguages
import java.util.Locale

fun languageCodes(languages: StreamLanguages): Set<String> = LanguageCodes.codes(languages)

object LanguageCodes {
    private const val REGIONAL_INDICATOR_MIN = 0x1F1E6
    private const val REGIONAL_INDICATOR_MAX = 0x1F1FF

    private val NAME_TO_CODE: Map<String, String> =
        mapOf(
            "english" to "en",
            "japanese" to "ja",
            "korean" to "ko",
            "chinese" to "zh",
            "mandarin" to "zh",
            "cantonese" to "zh",
            "hindi" to "hi",
            "tamil" to "ta",
            "telugu" to "te",
            "spanish" to "es",
            "latino" to "es",
            "french" to "fr",
            "german" to "de",
            "italian" to "it",
            "portuguese" to "pt",
            "russian" to "ru",
            "arabic" to "ar",
            "turkish" to "tr",
            "thai" to "th",
            "polish" to "pl",
            "dutch" to "nl",
            "swedish" to "sv",
            "norwegian" to "no",
            "danish" to "da",
            "finnish" to "fi",
            "czech" to "cs",
            "hungarian" to "hu",
            "hebrew" to "he",
            "indonesian" to "id",
            "vietnamese" to "vi",
        )

    private val COUNTRY_TO_CODE: Map<String, String> =
        mapOf(
            "GB" to "en",
            "US" to "en",
            "AU" to "en",
            "CA" to "en",
            "NZ" to "en",
            "IE" to "en",
            "FR" to "fr",
            "ES" to "es",
            "MX" to "es",
            "CO" to "es",
            "AR" to "es",
            "DE" to "de",
            "AT" to "de",
            "IT" to "it",
            "PT" to "pt",
            "BR" to "pt",
            "RU" to "ru",
            "UA" to "uk",
            "JP" to "ja",
            "KR" to "ko",
            "CN" to "zh",
            "TW" to "zh",
            "HK" to "zh",
            "IN" to "hi",
            "SA" to "ar",
            "AE" to "ar",
            "EG" to "ar",
            "TR" to "tr",
            "TH" to "th",
            "PL" to "pl",
            "NL" to "nl",
            "SE" to "sv",
            "NO" to "no",
            "DK" to "da",
            "FI" to "fi",
            "CZ" to "cs",
            "HU" to "hu",
            "IL" to "he",
            "ID" to "id",
            "VN" to "vi",
        )

    val SUPPORTED_LANGUAGE_CODES: List<String> =
        listOf(
            "en",
            "ar",
            "cs",
            "da",
            "de",
            "es",
            "fi",
            "fr",
            "he",
            "hi",
            "hu",
            "id",
            "it",
            "ja",
            "ko",
            "nl",
            "no",
            "pl",
            "pt",
            "ru",
            "sv",
            "ta",
            "te",
            "th",
            "tr",
            "uk",
            "vi",
            "zh",
        ).sortedBy { languageDisplayName(it) }

    fun codes(languages: StreamLanguages): Set<String> {
        val result = LinkedHashSet<String>()

        for (name in languages.names) {
            NAME_TO_CODE[name.lowercase(Locale.ROOT)]?.let { result.add(it) }
        }

        for (flag in languages.flags) {
            flagToCountryCode(flag)?.let { country ->
                COUNTRY_TO_CODE[country]?.let { result.add(it) }
            }
        }

        return result
    }

    fun languageDisplayName(code: String): String {
        val display = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        return if (display.isNotBlank() && !display.equals(code, ignoreCase = true)) {
            display.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            code.uppercase(Locale.ROOT)
        }
    }

    fun flagToCountryCode(flag: String): String? {
        if (flag.isEmpty()) return null
        val cp1 = flag.codePointAt(0)
        val len1 = Character.charCount(cp1)
        val cp2 = if (len1 < flag.length) flag.codePointAt(len1) else 0
        return if (cp1 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX &&
            cp2 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX
        ) {
            val c1 = ('A'.code + (cp1 - REGIONAL_INDICATOR_MIN)).toChar()
            val c2 = ('A'.code + (cp2 - REGIONAL_INDICATOR_MIN)).toChar()
            "$c1$c2"
        } else {
            null
        }
    }
}
