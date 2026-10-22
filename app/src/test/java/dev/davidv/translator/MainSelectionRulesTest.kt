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

class MainSelectionRulesTest {
  private fun lang(code: String) = Language(code, code, code, Script.LATIN, singleScript(Script.LATIN), code)

  private val en = lang("en")
  private val es = lang("es")
  private val fr = lang("fr")
  private val de = lang("de")
  private val be = lang("be")
  private val all = listOf(de, en, es, fr, be)

  // Installed en↔X directions; X→Y pivots through English like the native catalog.
  private fun availability(directions: Set<Pair<Language, Language>>): LanguageAvailabilityState {
    val direct = { from: Language, to: Language -> (from to to) in directions }
    val entries =
      all.map { language ->
        LanguageAvailabilityEntry(
          language,
          LangAvailability(
            hasFromEnglish = language.isEnglish || direct(en, language),
            hasToEnglish = language.isEnglish || direct(language, en),
            ocrFiles = false,
            dictionaryFiles = false,
          ),
        )
      }
    return LanguageAvailabilityState(
      hasLanguages = entries.any { !it.language.isEnglish && it.availability.translatorFiles },
      availableLanguages = entries,
    )
  }

  private fun rules(
    directions: Set<Pair<Language, Language>>,
    settings: AppSettings = AppSettings(),
    metadata: Map<Language, LanguageMetadata> = emptyMap(),
  ): MainSelectionRules {
    val direct = { from: Language, to: Language -> (from to to) in directions }
    val canTranslate = { from: Language, to: Language ->
      from == to || direct(from, to) || (direct(from, en) && direct(en, to))
    }
    return MainSelectionRules(availability(directions), settings, metadata, canTranslate)
  }

  private fun both(vararg languages: Language) = languages.flatMap { listOf(en to it, it to en) }.toSet()

  private fun ready(
    source: Language,
    vararg targets: Language,
  ) = MainSelection.Ready(source, TargetTabs(emptyList(), targets.first(), targets.drop(1)))

  @Test
  fun `nothing beyond english installed has no selection`() {
    assertEquals(MainSelection.NoLanguages, rules(emptySet()).initial())
  }

  @Test
  fun `initial uses the configured target and source`() {
    val settings = AppSettings(defaultTargetLanguageCode = "es", defaultSourceLanguageCode = "fr")
    assertEquals(ready(fr, es), rules(both(es, fr), settings).initial())
  }

  @Test
  fun `initial falls back to english when the configured target is not installed`() {
    val settings = AppSettings(defaultTargetLanguageCode = "de")
    assertEquals(ready(es, en), rules(both(es), settings).initial())
  }

  @Test
  fun `initial finds a pair when only the en to X direction is installed`() {
    assertEquals(ready(en, es), rules(setOf(en to es)).initial())
  }

  @Test
  fun `installing the first language turns no selection into a pair`() {
    assertEquals(ready(es, en), rules(both(es)).reconciled(MainSelection.NoLanguages))
  }

  @Test
  fun `deleting the last language drops the selection`() {
    assertEquals(MainSelection.NoLanguages, rules(emptySet()).reconciled(ready(es, en)))
  }

  @Test
  fun `an unaffected selection is kept as is`() {
    val current = ready(es, fr, en)
    assertEquals(current, rules(both(es, fr)).reconciled(current))
  }

  @Test
  fun `a deleted source is replaced by one that reaches the target`() {
    assertEquals(ready(fr, en), rules(both(fr)).reconciled(ready(es, en)))
  }

  @Test
  fun `a deleted tab is removed and the others are kept`() {
    assertEquals(ready(es, en), rules(both(es)).reconciled(ready(es, fr, en)))
  }

  @Test
  fun `a deleted sole tab is replaced by the configured target`() {
    val settings = AppSettings(defaultTargetLanguageCode = "de")
    assertEquals(ready(es, de), rules(both(es, de), settings).reconciled(ready(es, fr)))
  }

  @Test
  fun `a replacement tab prefers a favorite over alphabetical order`() {
    val metadata = mapOf(fr to LanguageMetadata(favorite = true))
    val settings = AppSettings(defaultTargetLanguageCode = "xx")
    assertEquals(ready(es, fr), rules(both(es, fr, de), settings, metadata).reconciled(ready(es, lang("it"))))
  }

  @Test
  fun `a source that is also a tab leaves the tabs`() {
    assertEquals(ready(fr, en), rules(both(es, fr)).normalized(fr, TargetTabs(emptyList(), fr, listOf(en))))
  }

  @Test
  fun `a source that is the sole tab gets an alternate target`() {
    val settings = AppSettings(defaultTargetLanguageCode = "es")
    assertEquals(ready(fr, es), rules(both(es, fr), settings).normalized(fr, TargetTabs.of(fr)))
  }

  @Test
  fun `picking a detected source moves the previous source to the target`() {
    val next = rules(both(es, fr)).withSource(ready(es, en), fr, FromLangChange.MovePreviousToTarget)
    assertEquals(ready(fr, es), next)
  }

  @Test
  fun `picking a source keeps the target by default`() {
    assertEquals(ready(fr, en), rules(both(es, fr)).withSource(ready(es, en), fr, FromLangChange.KeepTarget))
  }

  @Test
  fun `the previous source stays off the targets when the new source can't reach it`() {
    val next = rules(setOf(es to en, fr to en)).withSource(ready(es, en), fr, FromLangChange.MovePreviousToTarget)
    assertEquals(ready(fr, en), next)
  }

  @Test
  fun `a language with only its X to en model is a source and never a target`() {
    val state = availability(setOf(be to en))
    assertEquals(listOf(be), state.sourceLanguages())
    assertEquals(listOf(en), state.targetLanguages())
  }

  @Test
  fun `english is a side only once some other language is installed in that direction`() {
    val state = availability(setOf(en to es))
    assertEquals(listOf(en), state.sourceLanguages())
    assertEquals(listOf(es), state.targetLanguages())
  }

  @Test
  fun `a configured target with only its X to en model starts from english instead`() {
    val settings = AppSettings(defaultTargetLanguageCode = "be")
    assertEquals(ready(be, en), rules(setOf(be to en), settings).initial())
  }

  @Test
  fun `a configured source with only its en to X model is skipped`() {
    val settings = AppSettings(defaultTargetLanguageCode = "en", defaultSourceLanguageCode = "es")
    assertEquals(ready(fr, en), rules(setOf(en to es, fr to en), settings).initial())
  }

  @Test
  fun `a tab the new source can't reach is dropped`() {
    val next = rules(setOf(be to en, es to en)).normalized(es, TargetTabs(emptyList(), be, listOf(en)))
    assertEquals(ready(es, en), next)
  }

  @Test(expected = IllegalStateException::class)
  fun `a source that translates to nothing installed is a caller bug`() {
    rules(setOf(be to en)).normalized(en, TargetTabs.of(be))
  }

  @Test
  fun `a target with only its X to en model can't be swapped to the source`() {
    assertNull(rules(setOf(be to en)).swapped(ready(be, en)))
  }

  @Test
  fun `swapping a pair installed both ways flips it`() {
    assertEquals(ready(es, en), rules(both(es)).swapped(ready(en, es)))
  }

  @Test
  fun `a stored target with only its X to en model resets to english`() {
    val settings = AppSettings(defaultTargetLanguageCode = "be", defaultSourceLanguageCode = "be")
    val usable = usableDefaults(settings, availability(setOf(be to en)))
    assertEquals("en", usable.defaultTargetLanguageCode)
    assertEquals("be", usable.defaultSourceLanguageCode)
  }

  @Test
  fun `a stored source that can't translate out is replaced by an installed one`() {
    val settings = AppSettings(defaultTargetLanguageCode = "es", defaultSourceLanguageCode = "en")
    val usable = usableDefaults(settings, availability(setOf(be to en)))
    assertEquals("en", usable.defaultTargetLanguageCode)
    assertEquals("be", usable.defaultSourceLanguageCode)
  }

  @Test
  fun `an unset source is filled with an installed one`() {
    val usable = usableDefaults(AppSettings(), availability(both(es)))
    assertEquals("en", usable.defaultTargetLanguageCode)
    assertEquals("es", usable.defaultSourceLanguageCode)
  }

  @Test
  fun `a source equal to the target is replaced`() {
    val settings = AppSettings(defaultTargetLanguageCode = "en", defaultSourceLanguageCode = "en")
    val usable = usableDefaults(settings, availability(both(es)))
    assertEquals("es", usable.defaultSourceLanguageCode)
  }
}
