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
  BENGALI("bn", "ben", "Bengali", "Bengali"),
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
  INDONESIAN("id", "ind", "Indonesian", "Latin"),
  ITALIAN("it", "ita", "Italian", "Latin"),
  JAPANESE("ja", "jpn", "Japanese", "Japanese"),
  KANNADA("kn", "kan", "Kannada", "Kannada"),
  KOREAN("ko", "kor", "Korean", "Hangul"),
  LATVIAN("lv", "lav", "Latvian", "Latin"),
  LITHUANIAN("lt", "lit", "Lithuanian", "Latin"),
  MALAY("ms", "msa", "Malay", "Latin"),
  MALAYALAM("ml", "mal", "Malayalam", "Malayalam"),
  PERSIAN("fa", "fas", "Persian", "Arabic"),
  POLISH("pl", "pol", "Polish", "Latin"),
  PORTUGUESE("pt", "por", "Portuguese", "Latin"),
  ROMANIAN("ro", "ron", "Romanian", "Latin"),
  RUSSIAN("ru", "rus", "Russian", "Cyrillic"),
  SLOVAK("sk", "slk", "Slovak", "Latin"),
  SLOVENIAN("sl", "slv", "Slovenian", "Latin"),
  SPANISH("es", "spa", "Spanish", "Latin"),
  SWEDISH("sv", "swe", "Swedish", "Latin"),
  TAMIL("ta", "tam", "Tamil", "Tamil"),
  TELUGU("te", "tel", "Telugu", "Telugu"),
  TURKISH("tr", "tur", "Turkish", "Latin"),
  UKRAINIAN("uk", "ukr", "Ukrainian", "Cyrillic"),
  ;

  val tessFilename: String
    get() = "$tessName.traineddata"
}

data class LanguageFiles(
  val model: String,
  val srcVocab: String,
  val tgtVocab: String,
  val lex: String,
  val quality: ModelType,
) {
  fun allFiles(): List<String> = listOf(model, srcVocab, tgtVocab, lex).distinct()
}

val fromEnglishFiles =
  mapOf(
    Language.ARABIC to LanguageFiles("model.enar.intgemm.alphas.bin", "vocab.enar.spm", "vocab.enar.spm", "lex.50.50.enar.s2t.bin", ModelType.BASE),
    Language.AZERBAIJANI to LanguageFiles("model.enaz.intgemm.alphas.bin", "vocab.enaz.spm", "vocab.enaz.spm", "lex.50.50.enaz.s2t.bin", ModelType.TINY),
    Language.BULGARIAN to LanguageFiles("model.enbg.intgemm.alphas.bin", "vocab.enbg.spm", "vocab.enbg.spm", "lex.50.50.enbg.s2t.bin", ModelType.TINY),
    Language.BENGALI to LanguageFiles("model.enbn.intgemm.alphas.bin", "vocab.enbn.spm", "vocab.enbn.spm", "lex.50.50.enbn.s2t.bin", ModelType.TINY),
    Language.CATALAN to LanguageFiles("model.enca.intgemm.alphas.bin", "vocab.enca.spm", "vocab.enca.spm", "lex.50.50.enca.s2t.bin", ModelType.BASE_MEMORY),
    Language.CZECH to LanguageFiles("model.encs.intgemm.alphas.bin", "vocab.encs.spm", "vocab.encs.spm", "lex.50.50.encs.s2t.bin", ModelType.BASE),
    Language.DANISH to LanguageFiles("model.enda.intgemm.alphas.bin", "vocab.enda.spm", "vocab.enda.spm", "lex.50.50.enda.s2t.bin", ModelType.TINY),
    Language.GERMAN to LanguageFiles("model.ende.intgemm.alphas.bin", "vocab.ende.spm", "vocab.ende.spm", "lex.50.50.ende.s2t.bin", ModelType.TINY),
    Language.GREEK to LanguageFiles("model.enel.intgemm.alphas.bin", "vocab.enel.spm", "vocab.enel.spm", "lex.50.50.enel.s2t.bin", ModelType.TINY),
    Language.SPANISH to LanguageFiles("model.enes.intgemm.alphas.bin", "vocab.enes.spm", "vocab.enes.spm", "lex.50.50.enes.s2t.bin", ModelType.TINY),
    Language.ESTONIAN to LanguageFiles("model.enet.intgemm.alphas.bin", "vocab.enet.spm", "vocab.enet.spm", "lex.50.50.enet.s2t.bin", ModelType.TINY),
    Language.PERSIAN to LanguageFiles("model.enfa.intgemm.alphas.bin", "vocab.enfa.spm", "vocab.enfa.spm", "lex.50.50.enfa.s2t.bin", ModelType.TINY),
    Language.FINNISH to LanguageFiles("model.enfi.intgemm.alphas.bin", "vocab.enfi.spm", "vocab.enfi.spm", "lex.50.50.enfi.s2t.bin", ModelType.TINY),
    Language.FRENCH to LanguageFiles("model.enfr.intgemm.alphas.bin", "vocab.enfr.spm", "vocab.enfr.spm", "lex.50.50.enfr.s2t.bin", ModelType.TINY),
    Language.GUJARATI to LanguageFiles("model.engu.intgemm.alphas.bin", "vocab.engu.spm", "vocab.engu.spm", "lex.50.50.engu.s2t.bin", ModelType.TINY),
    Language.HEBREW to LanguageFiles("model.enhe.intgemm.alphas.bin", "vocab.enhe.spm", "vocab.enhe.spm", "lex.50.50.enhe.s2t.bin", ModelType.TINY),
    Language.HINDI to LanguageFiles("model.enhi.intgemm.alphas.bin", "vocab.enhi.spm", "vocab.enhi.spm", "lex.50.50.enhi.s2t.bin", ModelType.TINY),
    Language.CROATIAN to LanguageFiles("model.enhr.intgemm.alphas.bin", "vocab.enhr.spm", "vocab.enhr.spm", "lex.50.50.enhr.s2t.bin", ModelType.TINY),
    Language.HUNGARIAN to LanguageFiles("model.enhu.intgemm.alphas.bin", "vocab.enhu.spm", "vocab.enhu.spm", "lex.50.50.enhu.s2t.bin", ModelType.BASE_MEMORY),
    Language.INDONESIAN to LanguageFiles("model.enid.intgemm.alphas.bin", "vocab.enid.spm", "vocab.enid.spm", "lex.50.50.enid.s2t.bin", ModelType.TINY),
    Language.ITALIAN to LanguageFiles("model.enit.intgemm.alphas.bin", "vocab.enit.spm", "vocab.enit.spm", "lex.50.50.enit.s2t.bin", ModelType.TINY),
    Language.JAPANESE to LanguageFiles("model.enja.intgemm.alphas.bin", "srcvocab.enja.spm", "trgvocab.enja.spm", "lex.50.50.enja.s2t.bin", ModelType.BASE),
    Language.KANNADA to LanguageFiles("model.enkn.intgemm.alphas.bin", "vocab.enkn.spm", "vocab.enkn.spm", "lex.50.50.enkn.s2t.bin", ModelType.TINY),
    Language.KOREAN to LanguageFiles("model.enko.intgemm.alphas.bin", "vocab.enko.spm", "vocab.enko.spm", "lex.50.50.enko.s2t.bin", ModelType.BASE),
    Language.LITHUANIAN to LanguageFiles("model.enlt.intgemm.alphas.bin", "vocab.enlt.spm", "vocab.enlt.spm", "lex.50.50.enlt.s2t.bin", ModelType.BASE_MEMORY),
    Language.LATVIAN to LanguageFiles("model.enlv.intgemm.alphas.bin", "vocab.enlv.spm", "vocab.enlv.spm", "lex.50.50.enlv.s2t.bin", ModelType.BASE_MEMORY),
    Language.MALAYALAM to LanguageFiles("model.enml.intgemm.alphas.bin", "vocab.enml.spm", "vocab.enml.spm", "lex.50.50.enml.s2t.bin", ModelType.TINY),
    Language.MALAY to LanguageFiles("model.enms.intgemm.alphas.bin", "vocab.enms.spm", "vocab.enms.spm", "lex.50.50.enms.s2t.bin", ModelType.TINY),
    Language.DUTCH to LanguageFiles("model.ennl.intgemm.alphas.bin", "vocab.ennl.spm", "vocab.ennl.spm", "lex.50.50.ennl.s2t.bin", ModelType.TINY),
    Language.POLISH to LanguageFiles("model.enpl.intgemm.alphas.bin", "vocab.enpl.spm", "vocab.enpl.spm", "lex.50.50.enpl.s2t.bin", ModelType.TINY),
    Language.PORTUGUESE to LanguageFiles("model.enpt.intgemm.alphas.bin", "vocab.enpt.spm", "vocab.enpt.spm", "lex.50.50.enpt.s2t.bin", ModelType.TINY),
    Language.ROMANIAN to LanguageFiles("model.enro.intgemm.alphas.bin", "vocab.enro.spm", "vocab.enro.spm", "lex.50.50.enro.s2t.bin", ModelType.TINY),
    Language.RUSSIAN to LanguageFiles("model.enru.intgemm.alphas.bin", "vocab.enru.spm", "vocab.enru.spm", "lex.50.50.enru.s2t.bin", ModelType.BASE),
    Language.SLOVAK to LanguageFiles("model.ensk.intgemm.alphas.bin", "vocab.ensk.spm", "vocab.ensk.spm", "lex.50.50.ensk.s2t.bin", ModelType.BASE_MEMORY),
    Language.SLOVENIAN to LanguageFiles("model.ensl.intgemm.alphas.bin", "vocab.ensl.spm", "vocab.ensl.spm", "lex.50.50.ensl.s2t.bin", ModelType.BASE_MEMORY),
    Language.ALBANIAN to LanguageFiles("model.ensq.intgemm.alphas.bin", "vocab.ensq.spm", "vocab.ensq.spm", "lex.50.50.ensq.s2t.bin", ModelType.TINY),
    Language.SWEDISH to LanguageFiles("model.ensv.intgemm.alphas.bin", "vocab.ensv.spm", "vocab.ensv.spm", "lex.50.50.ensv.s2t.bin", ModelType.TINY),
    Language.TAMIL to LanguageFiles("model.enta.intgemm.alphas.bin", "vocab.enta.spm", "vocab.enta.spm", "lex.50.50.enta.s2t.bin", ModelType.TINY),
    Language.TELUGU to LanguageFiles("model.ente.intgemm.alphas.bin", "vocab.ente.spm", "vocab.ente.spm", "lex.50.50.ente.s2t.bin", ModelType.TINY),
    Language.TURKISH to LanguageFiles("model.entr.intgemm.alphas.bin", "vocab.entr.spm", "vocab.entr.spm", "lex.50.50.entr.s2t.bin", ModelType.TINY),
    Language.UKRAINIAN to LanguageFiles("model.enuk.intgemm.alphas.bin", "vocab.enuk.spm", "vocab.enuk.spm", "lex.50.50.enuk.s2t.bin", ModelType.BASE_MEMORY),
    Language.CHINESE to LanguageFiles("model.enzh.intgemm.alphas.bin", "srcvocab.enzh.spm", "trgvocab.enzh.spm", "lex.50.50.enzh.s2t.bin", ModelType.BASE),
  )

val toEnglishFiles =
  mapOf(
    Language.ARABIC to LanguageFiles("model.aren.intgemm.alphas.bin", "vocab.aren.spm", "vocab.aren.spm", "lex.50.50.aren.s2t.bin", ModelType.BASE),
    Language.AZERBAIJANI to LanguageFiles("model.azen.intgemm.alphas.bin", "vocab.azen.spm", "vocab.azen.spm", "lex.50.50.azen.s2t.bin", ModelType.TINY),
    Language.BULGARIAN to LanguageFiles("model.bgen.intgemm.alphas.bin", "vocab.bgen.spm", "vocab.bgen.spm", "lex.50.50.bgen.s2t.bin", ModelType.TINY),
    Language.BENGALI to LanguageFiles("model.bnen.intgemm.alphas.bin", "vocab.bnen.spm", "vocab.bnen.spm", "lex.50.50.bnen.s2t.bin", ModelType.TINY),
    Language.CATALAN to LanguageFiles("model.caen.intgemm.alphas.bin", "vocab.caen.spm", "vocab.caen.spm", "lex.50.50.caen.s2t.bin", ModelType.BASE_MEMORY),
    Language.CZECH to LanguageFiles("model.csen.intgemm.alphas.bin", "vocab.csen.spm", "vocab.csen.spm", "lex.50.50.csen.s2t.bin", ModelType.TINY),
    Language.DANISH to LanguageFiles("model.daen.intgemm.alphas.bin", "vocab.daen.spm", "vocab.daen.spm", "lex.50.50.daen.s2t.bin", ModelType.TINY),
    Language.GERMAN to LanguageFiles("model.deen.intgemm.alphas.bin", "vocab.deen.spm", "vocab.deen.spm", "lex.50.50.deen.s2t.bin", ModelType.BASE),
    Language.GREEK to LanguageFiles("model.elen.intgemm.alphas.bin", "vocab.elen.spm", "vocab.elen.spm", "lex.50.50.elen.s2t.bin", ModelType.TINY),
    Language.SPANISH to LanguageFiles("model.esen.intgemm.alphas.bin", "vocab.esen.spm", "vocab.esen.spm", "lex.50.50.esen.s2t.bin", ModelType.TINY),
    Language.ESTONIAN to LanguageFiles("model.eten.intgemm.alphas.bin", "vocab.eten.spm", "vocab.eten.spm", "lex.50.50.eten.s2t.bin", ModelType.TINY),
    Language.PERSIAN to LanguageFiles("model.faen.intgemm.alphas.bin", "vocab.faen.spm", "vocab.faen.spm", "lex.50.50.faen.s2t.bin", ModelType.TINY),
    Language.FINNISH to LanguageFiles("model.fien.intgemm.alphas.bin", "vocab.fien.spm", "vocab.fien.spm", "lex.50.50.fien.s2t.bin", ModelType.TINY),
    Language.FRENCH to LanguageFiles("model.fren.intgemm.alphas.bin", "vocab.fren.spm", "vocab.fren.spm", "lex.50.50.fren.s2t.bin", ModelType.TINY),
    Language.GUJARATI to LanguageFiles("model.guen.intgemm.alphas.bin", "vocab.guen.spm", "vocab.guen.spm", "lex.50.50.guen.s2t.bin", ModelType.TINY),
    Language.HEBREW to LanguageFiles("model.heen.intgemm.alphas.bin", "vocab.heen.spm", "vocab.heen.spm", "lex.50.50.heen.s2t.bin", ModelType.TINY),
    Language.HINDI to LanguageFiles("model.hien.intgemm.alphas.bin", "vocab.hien.spm", "vocab.hien.spm", "lex.50.50.hien.s2t.bin", ModelType.TINY),
    Language.CROATIAN to LanguageFiles("model.hren.intgemm.alphas.bin", "vocab.hren.spm", "vocab.hren.spm", "lex.50.50.hren.s2t.bin", ModelType.TINY),
    Language.HUNGARIAN to LanguageFiles("model.huen.intgemm.alphas.bin", "vocab.huen.spm", "vocab.huen.spm", "lex.50.50.huen.s2t.bin", ModelType.TINY),
    Language.INDONESIAN to LanguageFiles("model.iden.intgemm.alphas.bin", "vocab.iden.spm", "vocab.iden.spm", "lex.50.50.iden.s2t.bin", ModelType.TINY),
    Language.ITALIAN to LanguageFiles("model.iten.intgemm.alphas.bin", "vocab.iten.spm", "vocab.iten.spm", "lex.50.50.iten.s2t.bin", ModelType.TINY),
    Language.JAPANESE to LanguageFiles("model.jaen.intgemm.alphas.bin", "vocab.jaen.spm", "vocab.jaen.spm", "lex.50.50.jaen.s2t.bin", ModelType.BASE),
    Language.KANNADA to LanguageFiles("model.knen.intgemm.alphas.bin", "vocab.knen.spm", "vocab.knen.spm", "lex.50.50.knen.s2t.bin", ModelType.TINY),
    Language.KOREAN to LanguageFiles("model.koen.intgemm.alphas.bin", "vocab.koen.spm", "vocab.koen.spm", "lex.50.50.koen.s2t.bin", ModelType.BASE),
    Language.LITHUANIAN to LanguageFiles("model.lten.intgemm.alphas.bin", "vocab.lten.spm", "vocab.lten.spm", "lex.50.50.lten.s2t.bin", ModelType.TINY),
    Language.LATVIAN to LanguageFiles("model.lven.intgemm.alphas.bin", "vocab.lven.spm", "vocab.lven.spm", "lex.50.50.lven.s2t.bin", ModelType.TINY),
    Language.MALAYALAM to LanguageFiles("model.mlen.intgemm.alphas.bin", "vocab.mlen.spm", "vocab.mlen.spm", "lex.50.50.mlen.s2t.bin", ModelType.TINY),
    Language.MALAY to LanguageFiles("model.msen.intgemm.alphas.bin", "vocab.msen.spm", "vocab.msen.spm", "lex.50.50.msen.s2t.bin", ModelType.TINY),
    Language.DUTCH to LanguageFiles("model.nlen.intgemm.alphas.bin", "vocab.nlen.spm", "vocab.nlen.spm", "lex.50.50.nlen.s2t.bin", ModelType.BASE_MEMORY),
    Language.POLISH to LanguageFiles("model.plen.intgemm.alphas.bin", "vocab.plen.spm", "vocab.plen.spm", "lex.50.50.plen.s2t.bin", ModelType.TINY),
    Language.PORTUGUESE to LanguageFiles("model.pten.intgemm.alphas.bin", "vocab.pten.spm", "vocab.pten.spm", "lex.50.50.pten.s2t.bin", ModelType.TINY),
    Language.ROMANIAN to LanguageFiles("model.roen.intgemm.alphas.bin", "vocab.roen.spm", "vocab.roen.spm", "lex.50.50.roen.s2t.bin", ModelType.TINY),
    Language.RUSSIAN to LanguageFiles("model.ruen.intgemm.alphas.bin", "vocab.ruen.spm", "vocab.ruen.spm", "lex.50.50.ruen.s2t.bin", ModelType.TINY),
    Language.SLOVAK to LanguageFiles("model.sken.intgemm.alphas.bin", "vocab.sken.spm", "vocab.sken.spm", "lex.50.50.sken.s2t.bin", ModelType.TINY),
    Language.SLOVENIAN to LanguageFiles("model.slen.intgemm.alphas.bin", "vocab.slen.spm", "vocab.slen.spm", "lex.50.50.slen.s2t.bin", ModelType.BASE_MEMORY),
    Language.ALBANIAN to LanguageFiles("model.sqen.intgemm.alphas.bin", "vocab.sqen.spm", "vocab.sqen.spm", "lex.50.50.sqen.s2t.bin", ModelType.TINY),
    Language.SWEDISH to LanguageFiles("model.sven.intgemm.alphas.bin", "vocab.sven.spm", "vocab.sven.spm", "lex.50.50.sven.s2t.bin", ModelType.TINY),
    Language.TAMIL to LanguageFiles("model.taen.intgemm.alphas.bin", "vocab.taen.spm", "vocab.taen.spm", "lex.50.50.taen.s2t.bin", ModelType.TINY),
    Language.TELUGU to LanguageFiles("model.teen.intgemm.alphas.bin", "vocab.teen.spm", "vocab.teen.spm", "lex.50.50.teen.s2t.bin", ModelType.TINY),
    Language.TURKISH to LanguageFiles("model.tren.intgemm.alphas.bin", "vocab.tren.spm", "vocab.tren.spm", "lex.50.50.tren.s2t.bin", ModelType.TINY),
    Language.UKRAINIAN to LanguageFiles("model.uken.intgemm.alphas.bin", "vocab.uken.spm", "vocab.uken.spm", "lex.50.50.uken.s2t.bin", ModelType.TINY),
    Language.CHINESE to LanguageFiles("model.zhen.intgemm.alphas.bin", "vocab.zhen.spm", "vocab.zhen.spm", "lex.50.50.zhen.s2t.bin", ModelType.BASE),
  )
