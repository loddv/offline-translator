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

import android.content.Intent
import android.net.Uri
import android.os.Build

sealed interface SharedPayload {
  data class Stream(
    val uri: Uri,
  ) : SharedPayload

  data object Empty : SharedPayload
}

sealed interface SharedText : SharedPayload {
  data class Plain(
    val text: String,
  ) : SharedText

  data class WebUrl(
    val url: String,
  ) : SharedText

  data object UnsupportedDocumentUrl : SharedText
}

fun parseSharedText(text: String): SharedText {
  val trimmed = text.trim()
  if (!isWebUrl(trimmed)) return SharedText.Plain(text)
  if (isSupportedDocumentUrl(trimmed)) return SharedText.UnsupportedDocumentUrl
  return SharedText.WebUrl(trimmed)
}

fun parseSendIntent(intent: Intent): SharedPayload {
  val text = intent.getStringExtra(Intent.EXTRA_TEXT)
  if (text != null) return parseSharedText(text)
  val stream =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  if (stream != null) return SharedPayload.Stream(stream)
  return SharedPayload.Empty
}
