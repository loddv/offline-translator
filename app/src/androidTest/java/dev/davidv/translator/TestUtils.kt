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

import android.content.Context
import java.io.File

object TestUtils {
  fun copyFile(
    testContext: Context,
    appPath: File,
    filename: String,
  ) {
    val stream = testContext.assets.open(filename)
    val out = File(appPath, filename)
    out.outputStream().use { output ->
      stream.copyTo(output)
    }
    stream.close()
    println("Copied $filename to ${appPath.name}")
  }

  fun copyTessData(
    context: Context,
    tessDataPath: File,
    language: Language,
  ) {
    copyFile(context, tessDataPath, language.tessFilename)
  }

  fun copyLangData(
    context: Context,
    dataPath: File,
    language: Language,
  ) {
    val enLangFiles = fromEnglishFiles[language]
    enLangFiles?.allFiles()?.forEach {
      copyFile(context, dataPath, it)
    }

    val langEnFiles = toEnglishFiles[language]
    langEnFiles?.allFiles()?.forEach {
      copyFile(context, dataPath, it)
    }
  }
}