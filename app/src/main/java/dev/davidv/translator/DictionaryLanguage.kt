package dev.davidv.translator

fun Language.dictionaryCode(): String =
  when (this) {
    Language.CHINESE_TRA -> Language.CHINESE_SIM.code
    Language.BOSNIAN -> Language.CROATIAN.code
    Language.SERBIAN -> Language.CROATIAN.code
    else -> code
  }

fun DictionaryIndex.infoFor(language: Language): DictionaryInfo? = dictionaries[language.dictionaryCode()]
