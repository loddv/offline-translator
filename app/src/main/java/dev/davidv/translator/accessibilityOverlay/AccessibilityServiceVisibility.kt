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

package dev.davidv.translator.accessibilityOverlay

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Android lists every installed accessibility service; disabling the component is the only way to
// leave the list for users who never use the overlay. Back to DEFAULT restores the manifest's
// per-API-level enabled flag rather than forcing it on.
private fun serviceComponent(context: Context) = ComponentName(context, TranslatorAccessibilityService::class.java)

fun isAccessibilityServiceOffered(context: Context): Boolean =
  context.packageManager.getComponentEnabledSetting(serviceComponent(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

fun setAccessibilityServiceOffered(
  context: Context,
  offered: Boolean,
) {
  val state = if (offered) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
  context.packageManager.setComponentEnabledSetting(serviceComponent(context), state, PackageManager.DONT_KILL_APP)
}
