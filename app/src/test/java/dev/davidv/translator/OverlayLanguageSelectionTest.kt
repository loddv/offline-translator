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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class OverlayLanguageSelectionTest {
  private fun lang(code: String) = Language(code, code, code, Script.LATIN, singleScript(Script.LATIN), code)

  private val en = lang("en")
  private val es = lang("es")
  private val fr = lang("fr")
  private val ja = lang("ja")

  private val anyPair: (Language, Language) -> Boolean = { _, _ -> true }

  private fun entry(
    language: Language,
    translator: Boolean = true,
    ocr: Boolean = true,
  ) = LanguageAvailabilityEntry(
    language,
    LangAvailability(hasFromEnglish = translator, hasToEnglish = translator, ocrFiles = ocr, dictionaryFiles = false),
  )

  private fun availability(vararg entries: LanguageAvailabilityEntry) = LanguageAvailabilityState(true, entries.toList())

  private fun fixed(
    source: Language,
    target: Language,
  ) = OverlayLanguageSelection(OverlaySource.Fixed(source), target)

  @Test
  fun `defaults start on auto with the configured target`() {
    val settings = AppSettings(defaultTargetLanguageCode = "es", defaultSourceLanguageCode = "en")
    val result = OverlayLanguageSelection.defaults(availability(entry(en), entry(es)), settings)
    assertEquals(OverlayLanguageSelection(OverlaySource.Auto, es), result)
  }

  @Test
  fun `defaults fall back to english when the configured target left the catalog`() {
    val result = OverlayLanguageSelection.defaults(availability(entry(en), entry(es)), AppSettings(defaultTargetLanguageCode = "xx"))
    assertEquals(en, result.target)
  }

  @Test(expected = NoSuchElementException::class)
  fun `defaults fail loudly on a catalog without english`() {
    OverlayLanguageSelection.defaults(availability(entry(es)), AppSettings(defaultTargetLanguageCode = "xx"))
  }

  @Test
  fun `swap exchanges a fixed pair and makes the old target the source`() {
    assertEquals(fixed(en, es), fixed(es, en).swapped(anyPair))
  }

  @Test
  fun `swap is refused under auto`() {
    assertNull(OverlayLanguageSelection(OverlaySource.Auto, en).swapped(anyPair))
  }

  @Test
  fun `swap is refused when the catalog cannot run the reverse pair`() {
    var asked: Pair<Language, Language>? = null
    val result =
      fixed(es, en).swapped { from, to ->
        asked = from to to
        false
      }
    assertNull(result)
    assertEquals(es to en, asked)
  }

  @Test
  fun `swap drops the japanese reading order`() {
    val selection = fixed(ja, en).withNextReadingOrder()
    val swappedBack = selection.swapped(anyPair)!!.swapped(anyPair)!!
    assertNull(swappedBack.readingOrder)
  }

  @Test
  fun `picking a source leaves auto and keeps the target`() {
    assertEquals(fixed(fr, es), OverlayLanguageSelection(OverlaySource.Auto, es).withSource(fr))
  }

  @Test
  fun `setting auto forgets the fixed source and its reading order`() {
    val result = fixed(ja, en).withNextReadingOrder().withAutoSource()
    assertEquals(OverlayLanguageSelection(OverlaySource.Auto, en), result)
    assertNull(result.readingOrder)
  }

  @Test
  fun `picking a target keeps the source`() {
    assertEquals(fixed(es, fr), fixed(es, en).withTarget(fr))
  }

  @Test
  fun `re-picking the same japanese source keeps the reading order`() {
    val ordered = fixed(ja, en).withNextReadingOrder()
    assertEquals(ordered, ordered.withSource(ja))
  }

  @Test
  fun `picking another source clears the reading order`() {
    assertNull(fixed(ja, en).withNextReadingOrder().withSource(es).readingOrder)
  }

  @Test
  fun `reading order cycles auto, vertical, horizontal, auto`() {
    val start = fixed(ja, en)
    val orders = generateSequence(start) { it.withNextReadingOrder() }.take(4).map { it.readingOrder }.toList()
    assertEquals(listOf(null, ReadingOrder.TOP_TO_BOTTOM_RIGHT_TO_LEFT, ReadingOrder.LEFT_TO_RIGHT, null), orders)
  }

  @Test
  fun `reading order is only offered for a fixed japanese source`() {
    assertEquals(true, fixed(ja, en).offersReadingOrder)
    assertEquals(false, fixed(es, en).offersReadingOrder)
    assertEquals(false, OverlayLanguageSelection(OverlaySource.Auto, ja).offersReadingOrder)
  }

  @Test(expected = IllegalStateException::class)
  fun `toggling reading order on a non-japanese source fails loudly`() {
    fixed(es, en).withNextReadingOrder()
  }

  @Test(expected = IllegalArgumentException::class)
  fun `a reading order cannot be attached to a non-japanese source`() {
    OverlaySource.Fixed(es, ReadingOrder.LEFT_TO_RIGHT)
  }

  @Test
  fun `picker puts favourites first, then sorts by name, and requires ocr for sources`() {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.ENGLISH)
    try {
      val state = availability(entry(fr), entry(es, ocr = false), entry(ja), entry(lang("de"), translator = false))
      val metadata = mapOf(ja to LanguageMetadata(favorite = true))
      assertEquals(listOf(ja, fr), overlayPickerLanguages(state, metadata, OverlayLanguageSide.SOURCE))
      assertEquals(listOf(ja, fr, es), overlayPickerLanguages(state, metadata, OverlayLanguageSide.TARGET))
    } finally {
      Locale.setDefault(previous)
    }
  }
}
