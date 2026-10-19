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

class ReadonlyPopupSizeTest {
  @Test
  fun previouslyStoredFractionsParseUnchanged() {
    (4..16).forEach { step ->
      val stored = step / 20f
      assertEquals(ReadonlyPopupSize.Fraction(stored), ReadonlyPopupSize.fromSliderPosition(stored))
    }
  }

  @Test
  fun stepPastLargestFractionIsExpanded() {
    assertEquals(ReadonlyPopupSize.Expanded, ReadonlyPopupSize.fromSliderPosition(0.85f))
  }

  @Test
  fun sliderPositionRoundTrips() {
    val sizes = (4..16).map { ReadonlyPopupSize.Fraction(it / 20f) } + ReadonlyPopupSize.Expanded
    sizes.forEach { size ->
      assertEquals(size, ReadonlyPopupSize.fromSliderPosition(size.sliderPosition))
    }
  }

  @Test
  fun sliderStepsCoverEveryValue() {
    val range = ReadonlyPopupSize.SLIDER_RANGE
    val positions = ReadonlyPopupSize.SLIDER_STEPS + 2
    val sizes =
      (0 until positions).map { i ->
        val position = range.start + (range.endInclusive - range.start) * i / (positions - 1)
        ReadonlyPopupSize.fromSliderPosition(position)
      }
    assertEquals(ReadonlyPopupSize.Fraction(0.2f), sizes.first())
    assertEquals(ReadonlyPopupSize.Fraction(0.8f), sizes[sizes.size - 2])
    assertEquals(ReadonlyPopupSize.Expanded, sizes.last())
    assertEquals(sizes.size, sizes.toSet().size)
  }

  @Test
  fun outOfRangeValuesAreRejected() {
    assertNull(ReadonlyPopupSize.fromSliderPosition(0.1f))
    assertNull(ReadonlyPopupSize.fromSliderPosition(0.95f))
    assertNull(ReadonlyPopupSize.fromSliderPosition(Float.NaN))
  }

  @Test
  fun expandedOpensTheFullApp() {
    assertEquals(LaunchMode.Normal, readonlyLaunchMode(ReadonlyPopupSize.Expanded))
    assertEquals(LaunchMode.ReadonlyModal, readonlyLaunchMode(ReadonlyPopupSize.Fraction(0.35f)))
  }
}
