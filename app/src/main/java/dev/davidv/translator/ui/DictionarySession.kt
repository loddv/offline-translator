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

import android.util.Log
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageCatalog
import dev.davidv.translator.WordWithTaggedEntries
import dev.davidv.translator.localizedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface DictionaryState {
  data object Closed : DictionaryState

  // Following a word inside an entry stacks it on the previous one so back returns to it.
  data class Open(
    val language: Language,
    val word: WordWithTaggedEntries,
    val previous: List<WordWithTaggedEntries>,
  ) : DictionaryState {
    val stack: List<WordWithTaggedEntries> get() = previous + word
  }

  fun pushed(
    language: Language,
    word: WordWithTaggedEntries,
  ): Open =
    when (this) {
      Closed -> Open(language, word, emptyList())
      is Open -> Open(language, word, stack)
    }

  fun popped(): DictionaryState =
    when (this) {
      Closed -> Closed
      is Open -> previous.lastOrNull()?.let { copy(word = it, previous = previous.dropLast(1)) } ?: Closed
    }
}

class DictionarySession(
  private val catalog: StateFlow<LanguageCatalog>,
  private val uiEvents: UiEvents,
) {
  private val _state = MutableStateFlow<DictionaryState>(DictionaryState.Closed)
  val state: StateFlow<DictionaryState> = _state.asStateFlow()

  fun lookup(
    str: String,
    language: Language,
  ) {
    Log.i("DictionaryLookup", "Looking up $str for $language")
    val foundWord =
      try {
        catalog.value.lookupDictionary(language, str)
      } catch (e: uniffi.bindings.CatalogException.MissingAsset) {
        Log.i("DictionaryLookup", "No dictionary for ${language.displayName}", e)
        uiEvents.send(UiEvent.ShowToast("No ${language.localizedName()} dictionary installed"))
        return
      } catch (e: uniffi.bindings.CatalogException.Other) {
        Log.w("DictionaryLookup", "Lookup failed for ${language.displayName}", e)
        null
      }
    if (foundWord == null) {
      uiEvents.send(UiEvent.ShowToast("'$str' not found in ${language.code} dictionary"))
      Log.w("DictionaryLookup", "Lookup failed for ${language.displayName}")
      return
    }
    _state.update { it.pushed(language, foundWord) }
    Log.d("DictionaryLookup", "From lookup got $foundWord")
  }

  fun pop() {
    _state.update { it.popped() }
    Log.d("PopDictionary", "Popped dictionary")
  }

  fun clear() {
    _state.value = DictionaryState.Closed
    Log.d("ClearDictionaryStack", "Cleared dictionary stack")
  }
}
