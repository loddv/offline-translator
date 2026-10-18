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

package dev.davidv.translator.ui

import dev.davidv.translator.Language
import dev.davidv.translator.Script
import dev.davidv.translator.WordWithTaggedEntries
import dev.davidv.translator.singleScript
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTransitionsTest {
  private fun lang(code: String) = Language(code, code, code, Script.LATIN, singleScript(Script.LATIN), code)

  private fun word(text: String): WordWithTaggedEntries = WordWithTaggedEntries(text, 0, emptyList(), null, emptyList(), emptyList())

  private val es = lang("es")
  private val fr = lang("fr")

  @Test
  fun `a lookup opens the dictionary and a follow-up stacks on it`() {
    val opened = DictionaryState.Closed.pushed(es, word("casa"))
    val followed = opened.pushed(fr, word("maison"))
    assertEquals(DictionaryState.Open(es, word("casa"), emptyList()), opened)
    assertEquals(DictionaryState.Open(fr, word("maison"), listOf(word("casa"))), followed)
    assertEquals(listOf(word("casa"), word("maison")), followed.stack)
  }

  @Test
  fun `popping returns to the previous word and keeps the language`() {
    val state = DictionaryState.Closed.pushed(es, word("casa")).pushed(fr, word("maison"))
    assertEquals(DictionaryState.Open(fr, word("casa"), emptyList()), state.popped())
  }

  @Test
  fun `popping the last word closes the dictionary`() {
    assertEquals(DictionaryState.Closed, DictionaryState.Closed.pushed(es, word("casa")).popped())
    assertEquals(DictionaryState.Closed, DictionaryState.Closed.popped())
  }

  @Test
  fun `playback reports only move a live utterance forward`() {
    val loading = SpeechState.Loading(SpeechTarget.INPUT)
    val playing = SpeechState.Playing(SpeechTarget.INPUT)
    assertEquals(playing, loading.afterPlaybackReport(playing = true))
    assertEquals(SpeechState.Idle, playing.afterPlaybackReport(playing = false))
    // The replaced utterance's stop lands after the new one started loading.
    assertEquals(loading, loading.afterPlaybackReport(playing = false))
    assertEquals(SpeechState.Idle, SpeechState.Idle.afterPlaybackReport(playing = true))
  }

  @Test
  fun `a fresh detection replaces the prior one`() {
    val prior = LanguageDetection(es, 10)
    assertEquals(LanguageDetection(fr, 4), refreshedDetection(prior, "abcd", fr))
  }

  @Test
  fun `an inconclusive detection keeps the prior one while the text is as long`() {
    val prior = LanguageDetection(es, 4)
    assertEquals(prior, refreshedDetection(prior, "abcde", null))
    assertEquals(null, refreshedDetection(prior, "abc", null))
  }

  @Test
  fun `blank text drops the detection`() {
    assertEquals(null, refreshedDetection(LanguageDetection(es, 0), " ", fr))
  }

  @Test
  fun `translated document names carry the pair and a safe base name`() {
    assertEquals("my_report.es-fr.pdf", translatedDocumentFileName("my report!.pdf", es, fr))
    assertEquals("document.es-fr.txt", translatedDocumentFileName("???", es, fr))
  }
}
