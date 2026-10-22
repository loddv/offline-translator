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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LayoutDensity {
  Regular,

  // Tiny screens such as keypad flip phones (240x320 dp), where the regular
  // fixed paddings and chrome leave no room for the text itself.
  Compact,
  ;

  val dimens: Dimens
    get() =
      when (this) {
        Regular -> RegularDimens
        Compact -> CompactDimens
      }
}

private const val COMPACT_MAX_WIDTH_DP = 320

// Width only: a regular phone in landscape is short but has room for the
// regular chrome, while a 240dp-wide flip phone never does.
fun layoutDensityFor(screenWidthDp: Int): LayoutDensity =
  if (screenWidthDp < COMPACT_MAX_WIDTH_DP) {
    LayoutDensity.Compact
  } else {
    LayoutDensity.Regular
  }

data class Dimens(
  val screenHorizontalPadding: Dp,
  val screenBottomPadding: Dp,
  val sectionSpacing: Dp,
  val cardPaddingStart: Dp,
  val cardPaddingEnd: Dp,
  val cardPaddingTop: Dp,
  val cardPaddingBottom: Dp,
  val cardHeaderMinHeight: Dp,
  val cardBodyPaddingTop: Dp,
  val cardBodyPaddingEnd: Dp,
  val toolButtonSize: Dp,
  val toolIconSize: Dp,
  val languageRowButtonSize: Dp,
  val languageTitleFontSize: TextUnit,
  val languageSelectorMaxWidth: Dp,
  val languageSelectorVerticalPadding: Dp,
  val setupHorizontalPadding: Dp,
  val assetListHorizontalPadding: Dp,
  val assetRowIndent: Dp,
  val assetStatusClusterWidth: Dp,
  val assetStatusIndicatorSpacing: Dp,
  val assetRowButtonSize: Dp,
)

private val RegularDimens =
  Dimens(
    screenHorizontalPadding = 12.dp,
    screenBottomPadding = 8.dp,
    sectionSpacing = 12.dp,
    cardPaddingStart = 20.dp,
    cardPaddingEnd = 8.dp,
    cardPaddingTop = 8.dp,
    cardPaddingBottom = 12.dp,
    cardHeaderMinHeight = 40.dp,
    cardBodyPaddingTop = 4.dp,
    cardBodyPaddingEnd = 12.dp,
    toolButtonSize = 40.dp,
    toolIconSize = 24.dp,
    languageRowButtonSize = 36.dp,
    languageTitleFontSize = 18.sp,
    languageSelectorMaxWidth = 150.dp,
    languageSelectorVerticalPadding = 8.dp,
    setupHorizontalPadding = 16.dp,
    assetListHorizontalPadding = 12.dp,
    assetRowIndent = 28.dp,
    assetStatusClusterWidth = 60.dp,
    assetStatusIndicatorSpacing = 8.dp,
    assetRowButtonSize = 32.dp,
  )

private val CompactDimens =
  Dimens(
    screenHorizontalPadding = 4.dp,
    screenBottomPadding = 4.dp,
    sectionSpacing = 4.dp,
    cardPaddingStart = 8.dp,
    cardPaddingEnd = 4.dp,
    cardPaddingTop = 2.dp,
    cardPaddingBottom = 4.dp,
    cardHeaderMinHeight = 28.dp,
    cardBodyPaddingTop = 2.dp,
    cardBodyPaddingEnd = 4.dp,
    toolButtonSize = 28.dp,
    toolIconSize = 20.dp,
    languageRowButtonSize = 32.dp,
    languageTitleFontSize = 16.sp,
    languageSelectorMaxWidth = 84.dp,
    languageSelectorVerticalPadding = 4.dp,
    setupHorizontalPadding = 0.dp,
    assetListHorizontalPadding = 4.dp,
    assetRowIndent = 8.dp,
    assetStatusClusterWidth = 56.dp,
    assetStatusIndicatorSpacing = 2.dp,
    assetRowButtonSize = 28.dp,
  )

val LocalLayoutDensity = staticCompositionLocalOf { LayoutDensity.Regular }

object AppLayout {
  val density: LayoutDensity
    @Composable
    @ReadOnlyComposable
    get() = LocalLayoutDensity.current

  val dimens: Dimens
    @Composable
    @ReadOnlyComposable
    get() = LocalLayoutDensity.current.dimens
}
