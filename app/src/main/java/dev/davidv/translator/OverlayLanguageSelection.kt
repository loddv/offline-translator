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

private const val JAPANESE_CODE = "ja"

enum class OverlayLanguageSide { SOURCE, TARGET }

sealed interface OverlaySource {
  val readingOrder: ReadingOrder?

  // OCR applies a requested order to the whole page whatever it detects; the main screen asks
  // for one while auto-detecting because its last detection was Japanese.
  data class Auto(
    override val readingOrder: ReadingOrder?,
  ) : OverlaySource

  data class Fixed(
    val language: Language,
    override val readingOrder: ReadingOrder? = null,
  ) : OverlaySource {
    init {
      // Only the Japanese OCR model reads a reading order; on any other source it would be
      // silently dropped while the toolbar kept showing it.
      require(readingOrder == null || language.code == JAPANESE_CODE) {
        "reading order $readingOrder set for non-Japanese source ${language.code}"
      }
    }
  }
}

data class OverlayLanguageSelection(
  val source: OverlaySource,
  val target: Language,
) {
  val sourceLanguage: Language? get() = (source as? OverlaySource.Fixed)?.language

  val readingOrder: ReadingOrder? get() = source.readingOrder

  val isAutoSource: Boolean get() = source is OverlaySource.Auto

  val offersReadingOrder: Boolean get() = sourceLanguage?.code == JAPANESE_CODE

  fun withSource(language: Language): OverlayLanguageSelection =
    copy(source = OverlaySource.Fixed(language, readingOrder.takeIf { sourceLanguage == language }))

  fun withAutoSource(): OverlayLanguageSelection = copy(source = OverlaySource.Auto(readingOrder = null))

  fun withTarget(language: Language): OverlayLanguageSelection = copy(target = language)

  // Auto has no concrete language to move to the target side, so only a fixed pair swaps.
  fun swapped(canSwap: (from: Language, to: Language) -> Boolean): OverlayLanguageSelection? {
    val from = sourceLanguage ?: return null
    if (!canSwap(from, target)) return null
    return OverlayLanguageSelection(OverlaySource.Fixed(target), from)
  }

  fun withNextReadingOrder(): OverlayLanguageSelection {
    val fixed = source as? OverlaySource.Fixed
    check(fixed != null && offersReadingOrder) { "reading order toggled for source $source" }
    return copy(source = fixed.copy(readingOrder = nextReadingOrder(fixed.readingOrder)))
  }

  companion object {
    // Overlays start on auto-detect; the stored default source only seeds live screen translate,
    // which can't run the per-frame script classifier. The stored target code can name a
    // language a newer catalog dropped; English is the pivot every catalog carries.
    fun defaults(
      availability: LanguageAvailabilityState,
      settings: AppSettings,
    ): OverlayLanguageSelection {
      val languages = availability.allLanguages()
      val target =
        languages.firstOrNull { it.code == settings.defaultTargetLanguageCode }
          ?: languages.first { it.isEnglish }
      return OverlayLanguageSelection(OverlaySource.Auto(readingOrder = null), target)
    }
  }
}

fun nextReadingOrder(current: ReadingOrder?): ReadingOrder? =
  when (current) {
    null -> ReadingOrder.TOP_TO_BOTTOM_RIGHT_TO_LEFT
    ReadingOrder.TOP_TO_BOTTOM_RIGHT_TO_LEFT -> ReadingOrder.LEFT_TO_RIGHT
    ReadingOrder.LEFT_TO_RIGHT -> null
  }

// Overlay sources are always read through OCR, so a source without OCR models can't be offered.
fun overlayPickerLanguages(
  availability: LanguageAvailabilityState,
  metadata: Map<Language, LanguageMetadata>,
  side: OverlayLanguageSide,
): List<Language> =
  availability
    .translatorLanguages(requireOcr = side == OverlayLanguageSide.SOURCE)
    .sortedWith(
      compareByDescending<Language> { metadata[it]?.favorite ?: false }
        .thenBy(languageNameComparator()) { it.localizedName() },
    )
