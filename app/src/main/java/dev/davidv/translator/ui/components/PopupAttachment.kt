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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

enum class AttachmentSide {
  Above,
  Below,
}

// The popup is measured against the full incoming constraints, as if it were
// alone, so its fractional size never shrinks to make room for the attachment;
// the attachment takes the popup's width and whatever height is left over.
// The reported size covers both so the attachment stays inside the hit-test
// bounds and receives touches.
@Composable
fun PopupWithAttachment(
  side: AttachmentSide,
  attachment: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  popup: @Composable () -> Unit,
) {
  Layout(contents = listOf(popup, attachment), modifier = modifier) { (popupMeasurables, attachmentMeasurables), constraints ->
    check(attachmentMeasurables.size <= 1) { "popup attachment must emit at most one node" }
    val popupPlaceable = popupMeasurables.single().measure(constraints)
    val gap = 8.dp.roundToPx()
    val attachmentPlaceable =
      attachmentMeasurables.firstOrNull()?.measure(
        Constraints(
          minWidth = popupPlaceable.width,
          maxWidth = popupPlaceable.width,
          maxHeight = (constraints.maxHeight - popupPlaceable.height - gap).coerceAtLeast(0),
        ),
      )
    if (attachmentPlaceable == null) {
      return@Layout layout(popupPlaceable.width, popupPlaceable.height) { popupPlaceable.place(0, 0) }
    }
    val attachmentSpan = attachmentPlaceable.height + gap
    layout(popupPlaceable.width, popupPlaceable.height + attachmentSpan) {
      when (side) {
        AttachmentSide.Above -> {
          attachmentPlaceable.place(0, 0)
          popupPlaceable.place(0, attachmentSpan)
        }
        AttachmentSide.Below -> {
          popupPlaceable.place(0, 0)
          attachmentPlaceable.place(0, popupPlaceable.height + gap)
        }
      }
    }
  }
}
