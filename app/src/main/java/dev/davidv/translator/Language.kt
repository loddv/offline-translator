/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.translator

enum class ModelType(private val pathName: String) {
  BASE("base"),
  BASE_MEMORY("base-memory"),
  TINY("tiny"),
  ;

  override fun toString(): String = pathName
}

enum class Language(val code: String, val tessName: String, val displayName: String, val script: String) {
  ALBANIAN("sq", "sqi", "Albanian", "Latin"),
  ARABIC("ar", "ara", "Arabic", "Arabic"),
  AZERBAIJANI("az", "aze", "Azerbaijani", "Latin"),
  BELARUSIAN("be", "bel", "Belarusian", "Cyrillic"),
  BENGALI("bn", "ben", "Bengali", "Bengali"),
  BOSNIAN("bs", "bos", "Bosnian", "Latin"),
  BULGARIAN("bg", "bul", "Bulgarian", "Cyrillic"),
  CATALAN("ca", "cat", "Catalan", "Latin"),
  CHINESE("zh", "chi_sim", "Chinese", "Han"),
  CROATIAN("hr", "hrv", "Croatian", "Latin"),
  CZECH("cs", "ces", "Czech", "Latin"),
  DANISH("da", "dan", "Danish", "Latin"),
  DUTCH("nl", "nld", "Dutch", "Latin"),
  ENGLISH("en", "eng", "English", "Latin"),
  ESTONIAN("et", "est", "Estonian", "Latin"),
  FINNISH("fi", "fin", "Finnish", "Latin"),
  FRENCH("fr", "fra", "French", "Latin"),
  GERMAN("de", "deu", "German", "Latin"),
  GREEK("el", "ell", "Greek", "Greek"),
  GUJARATI("gu", "guj", "Gujarati", "Gujarati"),
  HEBREW("he", "heb", "Hebrew", "Hebrew"),
  HINDI("hi", "hin", "Hindi", "Devanagari"),
  HUNGARIAN("hu", "hun", "Hungarian", "Latin"),
  ICELANDIC("is", "isl", "Icelandic", "Latin"),
  INDONESIAN("id", "ind", "Indonesian", "Latin"),
  ITALIAN("it", "ita", "Italian", "Latin"),
  JAPANESE("ja", "jpn", "Japanese", "Japanese"),
  KANNADA("kn", "kan", "Kannada", "Kannada"),
  KOREAN("ko", "kor", "Korean", "Hangul"),
  LATVIAN("lv", "lav", "Latvian", "Latin"),
  LITHUANIAN("lt", "lit", "Lithuanian", "Latin"),
  MALAY("ms", "msa", "Malay", "Latin"),
  MALAYALAM("ml", "mal", "Malayalam", "Malayalam"),
  MALTESE("mt", "mlt", "Maltese", "Latin"),
  NORWEGIAN_BOKMAL("nb", "nor", "Norwegian Bokmål", "Latin"),
  NORWEGIAN_NYNORSK("nn", "nor", "Norwegian Nynorsk", "Latin"),
  PERSIAN("fa", "fas", "Persian", "Arabic"),
  POLISH("pl", "pol", "Polish", "Latin"),
  PORTUGUESE("pt", "por", "Portuguese", "Latin"),
  ROMANIAN("ro", "ron", "Romanian", "Latin"),
  RUSSIAN("ru", "rus", "Russian", "Cyrillic"),
  SERBIAN("sr", "srp", "Serbian", "Cyrillic"),
  SLOVAK("sk", "slk", "Slovak", "Latin"),
  SLOVENIAN("sl", "slv", "Slovenian", "Latin"),
  SPANISH("es", "spa", "Spanish", "Latin"),
  SWEDISH("sv", "swe", "Swedish", "Latin"),
  TAMIL("ta", "tam", "Tamil", "Tamil"),
  TELUGU("te", "tel", "Telugu", "Telugu"),
  TURKISH("tr", "tur", "Turkish", "Latin"),
  UKRAINIAN("uk", "ukr", "Ukrainian", "Cyrillic"),
}

val fromEnglish =
  mapOf(
    Language.ARABIC to ModelType.BASE,
    Language.AZERBAIJANI to ModelType.TINY,
    Language.BULGARIAN to ModelType.TINY,
    Language.BENGALI to ModelType.TINY,
    Language.CATALAN to ModelType.BASE_MEMORY,
    Language.CZECH to ModelType.BASE,
    Language.DANISH to ModelType.TINY,
    Language.GERMAN to ModelType.TINY,
    Language.GREEK to ModelType.TINY,
    Language.SPANISH to ModelType.TINY,
    Language.ESTONIAN to ModelType.TINY,
    Language.PERSIAN to ModelType.TINY,
    Language.FINNISH to ModelType.TINY,
    Language.FRENCH to ModelType.TINY,
    Language.GUJARATI to ModelType.TINY,
    Language.HEBREW to ModelType.TINY,
    Language.HINDI to ModelType.TINY,
    Language.CROATIAN to ModelType.TINY,
    Language.HUNGARIAN to ModelType.BASE_MEMORY,
    Language.INDONESIAN to ModelType.TINY,
    Language.ITALIAN to ModelType.TINY,
    Language.JAPANESE to ModelType.BASE,
    Language.KANNADA to ModelType.TINY,
    Language.KOREAN to ModelType.BASE,
    Language.LITHUANIAN to ModelType.BASE_MEMORY,
    Language.LATVIAN to ModelType.BASE_MEMORY,
    Language.MALAYALAM to ModelType.TINY,
    Language.MALAY to ModelType.TINY,
    Language.DUTCH to ModelType.TINY,
    Language.POLISH to ModelType.TINY,
    Language.PORTUGUESE to ModelType.TINY,
    Language.ROMANIAN to ModelType.TINY,
    Language.RUSSIAN to ModelType.BASE,
    Language.SLOVAK to ModelType.BASE_MEMORY,
    Language.SLOVENIAN to ModelType.BASE_MEMORY,
    Language.ALBANIAN to ModelType.TINY,
    Language.SWEDISH to ModelType.TINY,
    Language.TAMIL to ModelType.TINY,
    Language.TELUGU to ModelType.TINY,
    Language.TURKISH to ModelType.TINY,
    Language.UKRAINIAN to ModelType.BASE_MEMORY,
    Language.CHINESE to ModelType.BASE,
  )

val toEnglish =
  mapOf(
    Language.ARABIC to ModelType.BASE,
    Language.AZERBAIJANI to ModelType.TINY,
    Language.BULGARIAN to ModelType.TINY,
    Language.BENGALI to ModelType.TINY,
    Language.CATALAN to ModelType.BASE_MEMORY,
    Language.CZECH to ModelType.TINY,
    Language.DANISH to ModelType.TINY,
    Language.GERMAN to ModelType.BASE,
    Language.GREEK to ModelType.TINY,
    Language.SPANISH to ModelType.TINY,
    Language.ESTONIAN to ModelType.TINY,
    Language.PERSIAN to ModelType.TINY,
    Language.FINNISH to ModelType.TINY,
    Language.FRENCH to ModelType.TINY,
    Language.GUJARATI to ModelType.TINY,
    Language.HEBREW to ModelType.TINY,
    Language.HINDI to ModelType.TINY,
    Language.CROATIAN to ModelType.TINY,
    Language.HUNGARIAN to ModelType.TINY,
    Language.INDONESIAN to ModelType.TINY,
    Language.ITALIAN to ModelType.TINY,
    Language.JAPANESE to ModelType.BASE,
    Language.KANNADA to ModelType.TINY,
    Language.KOREAN to ModelType.BASE,
    Language.LITHUANIAN to ModelType.TINY,
    Language.LATVIAN to ModelType.TINY,
    Language.MALAYALAM to ModelType.TINY,
    Language.MALAY to ModelType.TINY,
    Language.DUTCH to ModelType.BASE_MEMORY,
    Language.POLISH to ModelType.TINY,
    Language.PORTUGUESE to ModelType.TINY,
    Language.ROMANIAN to ModelType.TINY,
    Language.RUSSIAN to ModelType.TINY,
    Language.SLOVAK to ModelType.TINY,
    Language.SLOVENIAN to ModelType.BASE_MEMORY,
    Language.ALBANIAN to ModelType.TINY,
    Language.SWEDISH to ModelType.TINY,
    Language.TAMIL to ModelType.TINY,
    Language.TELUGU to ModelType.TINY,
    Language.TURKISH to ModelType.TINY,
    Language.UKRAINIAN to ModelType.TINY,
    Language.CHINESE to ModelType.BASE,
  )
