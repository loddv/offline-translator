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

package dev.davidv.translator.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davidv.translator.Language
import dev.davidv.translator.R
import dev.davidv.translator.TranslatorMessage

private class InputSource(
  val iconRes: Int,
  val label: String,
  val onClick: () -> Unit,
)

// Screen (experimental, gated), voice, camera, photos and documents, in the
// order both the row and the overflow menu present them.
@Composable
private fun rememberInputSources(
  input: String,
  from: Language,
  onMessage: (TranslatorMessage) -> Unit,
  actions: ImageSourceActions,
  onCameraClick: () -> Unit,
  screenTranslateEnabled: Boolean,
  isAutoSource: Boolean,
): List<InputSource> {
  val context = LocalContext.current
  val speechInput = rememberSpeechInput(input = input, from = from, onMessage = onMessage)
  val screen =
    InputSource(
      iconRes = R.drawable.videocam,
      label = stringResource(R.string.a11y_translate_screen_live),
      onClick = {
        if (isAutoSource) {
          Toast.makeText(context, context.getString(R.string.screen_translate_needs_source), Toast.LENGTH_LONG).show()
        } else {
          actions.startScreen()
        }
      },
    ).takeIf { screenTranslateEnabled }
  val voice =
    speechInput?.let {
      InputSource(iconRes = R.drawable.mic, label = stringResource(R.string.a11y_voice_input), onClick = it)
    }
  return listOfNotNull(
    screen,
    voice,
    InputSource(R.drawable.camera, stringResource(R.string.a11y_source_camera), onCameraClick),
    InputSource(R.drawable.gallery, stringResource(R.string.a11y_source_photos), actions.pickPhotos),
    InputSource(R.drawable.draft, stringResource(R.string.a11y_source_document), actions.pickDocs),
  )
}

// The compact source row under the input field: screen (experimental, gated),
// voice, camera, photos and documents. It absorbs what used to be the FAB's
// image-source bottom sheet plus the standalone voice button.
@Composable
fun InputSourceRow(
  input: String,
  from: Language,
  onMessage: (TranslatorMessage) -> Unit,
  actions: ImageSourceActions,
  onCameraClick: () -> Unit,
  screenTranslateEnabled: Boolean,
  isAutoSource: Boolean,
  modifier: Modifier = Modifier,
) {
  val sources =
    rememberInputSources(input, from, onMessage, actions, onCameraClick, screenTranslateEnabled, isAutoSource)

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    sources.forEach { source ->
      ActionPillButton(
        iconRes = source.iconRes,
        contentDescription = source.label,
        onClick = source.onClick,
      )
    }
  }
}

// The same sources folded into a card-header overflow button, for screens too
// short to spare the row.
@Composable
fun InputSourceMenu(
  input: String,
  from: Language,
  onMessage: (TranslatorMessage) -> Unit,
  actions: ImageSourceActions,
  onCameraClick: () -> Unit,
  screenTranslateEnabled: Boolean,
  isAutoSource: Boolean,
) {
  val sources =
    rememberInputSources(input, from, onMessage, actions, onCameraClick, screenTranslateEnabled, isAutoSource)
  var expanded by remember { mutableStateOf(false) }

  Box {
    ToolIconButton(
      iconRes = R.drawable.more_vert,
      contentDescription = stringResource(R.string.a11y_input_sources),
      onClick = { expanded = true },
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      sources.forEach { source ->
        DropdownMenuItem(
          text = { Text(source.label) },
          leadingIcon = {
            Icon(
              painterResource(id = source.iconRes),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
          },
          onClick = {
            expanded = false
            source.onClick()
          },
        )
      }
    }
  }
}
