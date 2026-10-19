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

import kotlin.math.roundToInt

sealed interface ReadonlyPopupSize {
  data class Fraction(
    val heightFactor: Float,
  ) : ReadonlyPopupSize {
    init {
      require(heightFactor in MIN_FRACTION..MAX_FRACTION) { "popup height factor $heightFactor out of range" }
    }
  }

  data object Expanded : ReadonlyPopupSize

  val sliderPosition: Float
    get() =
      when (this) {
        is Fraction -> heightFactor
        Expanded -> EXPANDED_POSITION
      }

  companion object {
    private const val STEPS_PER_UNIT = 20
    private const val MIN_STEP = 4
    private const val MAX_STEP = 16
    private const val EXPANDED_STEP = MAX_STEP + 1

    const val MIN_FRACTION = MIN_STEP.toFloat() / STEPS_PER_UNIT
    const val MAX_FRACTION = MAX_STEP.toFloat() / STEPS_PER_UNIT

    // Expanded is persisted as the slider step right after the largest fraction, so the
    // preference stays a plain float and every previously stored fraction parses unchanged.
    const val EXPANDED_POSITION = EXPANDED_STEP.toFloat() / STEPS_PER_UNIT

    val SLIDER_RANGE = MIN_FRACTION..EXPANDED_POSITION
    const val SLIDER_STEPS = EXPANDED_STEP - MIN_STEP - 1

    val DEFAULT: ReadonlyPopupSize = Fraction(0.35f)

    fun fromSliderPosition(position: Float): ReadonlyPopupSize? {
      if (position.isNaN()) return null
      val step = (position * STEPS_PER_UNIT).roundToInt()
      return when (step) {
        in MIN_STEP..MAX_STEP -> Fraction(step.toFloat() / STEPS_PER_UNIT)
        EXPANDED_STEP -> Expanded
        else -> null
      }
    }
  }
}

fun readonlyLaunchMode(size: ReadonlyPopupSize): LaunchMode =
  when (size) {
    is ReadonlyPopupSize.Fraction -> LaunchMode.ReadonlyModal
    ReadonlyPopupSize.Expanded -> LaunchMode.Normal
  }
