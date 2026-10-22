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
package dev.davidv.translator.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutDensityTest {
  @Test
  fun flipPhoneIsCompact() {
    assertEquals(LayoutDensity.Compact, layoutDensityFor(screenWidthDp = 240))
  }

  @Test
  fun regularPhoneIsRegular() {
    assertEquals(LayoutDensity.Regular, layoutDensityFor(screenWidthDp = 411))
  }

  @Test
  fun landscapePhoneIsRegular() {
    assertEquals(LayoutDensity.Regular, layoutDensityFor(screenWidthDp = 843))
  }

  @Test
  fun thresholdIsExclusive() {
    assertEquals(LayoutDensity.Regular, layoutDensityFor(screenWidthDp = 320))
  }
}
