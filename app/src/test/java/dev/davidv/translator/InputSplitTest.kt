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

class InputSplitTest {
  @Test
  fun dragIsClampedToBounds() {
    assertEquals(InputSplit.MAX, InputSplit.DEFAULT.dragged(1f).fraction)
    assertEquals(InputSplit.MIN, InputSplit.DEFAULT.dragged(-1f).fraction)
    assertEquals(0.6f, InputSplit.DEFAULT.dragged(0.1f).fraction, 1e-6f)
  }

  @Test
  fun parseRejectsOutOfRange() {
    assertEquals(InputSplit(0.3f), InputSplit.parse(0.3f))
    assertNull(InputSplit.parse(0.9f))
    assertNull(InputSplit.parse(Float.NaN))
  }
}
