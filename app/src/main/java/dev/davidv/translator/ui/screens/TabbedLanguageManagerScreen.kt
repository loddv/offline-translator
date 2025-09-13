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

package dev.davidv.translator.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.DownloadState
import dev.davidv.translator.LangAvailability
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageAvailabilityState
import dev.davidv.translator.LanguageManagerScreen
import dev.davidv.translator.createPreviewStates
import dev.davidv.translator.fromEnglishFiles
import dev.davidv.translator.ui.components.LanguageEvent
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun TabbedLanguageManagerScreen(
  installedLanguages: List<Language>,
  availableLanguages: List<Language>,
  languageAvailabilityState: LanguageAvailabilityState,
  downloadStates: Map<Language, DownloadState>,
  dictionaryDownloadStates: Map<Language, DownloadState>,
  onLanguageEvent: (LanguageEvent) -> Unit,
  onDictionaryEvent: (LanguageEvent) -> Unit,
) {
  var selectedTabIndex by remember { mutableIntStateOf(0) }

  val installedDictionaries =
    (installedLanguages + Language.ENGLISH).filter { lang ->
      languageAvailabilityState.availableLanguageMap[lang]?.dictionaryFiles == true
    }
  val availableDictionaries =
    (installedLanguages + Language.ENGLISH).filter { lang ->
      languageAvailabilityState.availableLanguageMap[lang]?.dictionaryFiles == false
    }

  Scaffold(
    modifier =
      Modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .imePadding()
        .padding(top = 32.dp),
    // FIXME
    topBar = {
      TabRow(selectedTabIndex = selectedTabIndex) {
        Tab(
          selected = selectedTabIndex == 0,
          onClick = { selectedTabIndex = 0 },
          text = { Text("Languages") },
        )
        Tab(
          selected = selectedTabIndex == 1,
          onClick = { selectedTabIndex = 1 },
          text = { Text("Dictionaries") },
        )
      }
    },
  ) { scaffoldPaddingValues ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .navigationBarsPadding()
          .imePadding()
          .padding(scaffoldPaddingValues)
          .padding(bottom = 8.dp),
    ) {
      when (selectedTabIndex) {
        0 -> {
          LanguageManagerScreen(
            embedded = true,
            title = "Language Packs",
            installedLanguages = installedLanguages,
            availableLanguages = availableLanguages,
            languageAvailabilityState = languageAvailabilityState,
            downloadStates = downloadStates,
            availabilityCheck = { it.translatorFiles },
            onEvent = onLanguageEvent,
          )
        }

        1 -> {
          LanguageManagerScreen(
            embedded = true,
            title = "Dictionary Packs",
            installedLanguages = installedDictionaries,
            availableLanguages = availableDictionaries,
            languageAvailabilityState = languageAvailabilityState,
            downloadStates = dictionaryDownloadStates,
            availabilityCheck = { it.dictionaryFiles },
            onEvent = { ev ->
              when (ev) {
                is LanguageEvent.Download -> onDictionaryEvent(LanguageEvent.DownloadDictionary(ev.language))
                is LanguageEvent.Delete -> onDictionaryEvent(LanguageEvent.DeleteDictionary(ev.language))
                else -> {
                  Log.i("LanguageManager", "Got unexpected event $ev")
                }
              }
            },
          )
        }
      }
    }
  }
}

@Composable
@Preview
fun TabbedLanguageManagerPreview() {
  val states = createPreviewStates()
  val languageAvailabilityState by states.languageState.collectAsState()
  val downloadStates by states.downloadStates.collectAsState()

  // Create mock data with some installed languages and dictionaries
  val mockLanguageState =
    LanguageAvailabilityState(
      availableLanguageMap =
        mapOf(
          Language.ENGLISH to LangAvailability(true, true, true),
          Language.FRENCH to LangAvailability(true, true, true),
          Language.SPANISH to LangAvailability(true, true, false),
          Language.GERMAN to LangAvailability(true, true, false),
        ),
    )

  val availLangs = mockLanguageState.availableLanguageMap.filterValues { it.translatorFiles }.keys
  val installedLanguages = availLangs.filter { it != Language.ENGLISH }.sortedBy { it.displayName }
  val availableLanguages =
    Language.entries
      .filter { lang ->
        fromEnglishFiles[lang] != null && !availLangs.contains(lang) && lang != Language.ENGLISH
      }.sortedBy { it.displayName }

  TranslatorTheme {
    TabbedLanguageManagerScreen(
      installedLanguages = installedLanguages,
      availableLanguages = availableLanguages,
      languageAvailabilityState = mockLanguageState,
      downloadStates = downloadStates,
      dictionaryDownloadStates =
        mapOf(
          Language.SPANISH to DownloadState(isDownloading = true, totalSize = 1000000, downloaded = 500000),
        ),
      onLanguageEvent = {},
      onDictionaryEvent = {},
    )
  }
}
