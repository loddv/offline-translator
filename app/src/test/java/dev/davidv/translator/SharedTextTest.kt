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
import org.junit.Test

class SharedTextTest {
  @Test
  fun plainTextIsKeptVerbatim() {
    assertEquals(SharedText.Plain("  hola mundo\n"), parseSharedText("  hola mundo\n"))
  }

  @Test
  fun bareUrlIsTrimmedWebUrl() {
    assertEquals(SharedText.WebUrl("https://example.com/article"), parseSharedText(" https://example.com/article\n"))
  }

  @Test
  fun titleWithUrlIsPlainText() {
    val shared = "Some headline https://example.com/article"
    assertEquals(SharedText.Plain(shared), parseSharedText(shared))
  }

  @Test
  fun documentUrlIsUnsupported() {
    assertEquals(SharedText.UnsupportedDocumentUrl, parseSharedText("https://example.com/paper.pdf"))
    assertEquals(SharedText.UnsupportedDocumentUrl, parseSharedText("http://example.com/photo.JPG"))
  }

  @Test
  fun nonHttpSchemeIsPlainText() {
    assertEquals(SharedText.Plain("ftp://example.com/x"), parseSharedText("ftp://example.com/x"))
  }
}
