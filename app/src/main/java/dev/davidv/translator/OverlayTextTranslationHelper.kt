package dev.davidv.translator

class OverlayTextTranslationHelper(
  private val langStateManager: LanguageStateManager,
  private val languageMetadataManager: LanguageMetadataManager,
) {
  fun availableLanguages(isSource: Boolean): List<Language> {
    val metadata = languageMetadataManager.metadata.value
    return langStateManager.languageState.value
      .translatorLanguages(requireOcr = isSource)
      .sortedWith(
        compareByDescending<Language> { metadata[it]?.favorite ?: false }
          .thenBy(languageNameComparator()) { it.localizedName() },
      )
  }
}
