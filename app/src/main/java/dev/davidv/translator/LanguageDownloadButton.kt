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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LanguageDownloadButton(
    language: Language,
    downloadState: DownloadState?,
    context: Context,
    isLanguageAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadState?.isDownloading == true
    val isCompleted = downloadState?.isCompleted == true
    
    if (isDownloading) {
        // Progress indicator with cancel button
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(48.dp)
        ) {
            val targetProgress = downloadState?.progress ?: 1f
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 300),
                label = "progress"
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(48.dp)
            )
            IconButton(
                onClick = {
                    DownloadService.cancelDownload(context, language)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painterResource(id = R.drawable.cancel),
                    contentDescription = "Cancel Download",
                )
            }
        }
    } else if (isLanguageAvailable || isCompleted) {
        // Delete button for available/completed languages
        IconButton(
            onClick = {
                DownloadService.deleteLanguage(context, language)
            },
            modifier = modifier
        ) {
            Icon(
                painterResource(id = R.drawable.delete),
                contentDescription = "Delete Language",
            )
        }
    } else {
        // Download/retry button
        IconButton(
            onClick = {
                DownloadService.startDownload(context, language)
            },
            enabled = true,
            modifier = modifier
        ) {
            when {
                downloadState?.isCancelled == true || downloadState?.error != null -> {
                    Icon(
                        painterResource(id = R.drawable.refresh),
                        contentDescription = "Retry Download"
                    )
                }
                else -> {
                    Icon(
                        painterResource(id = R.drawable.add),
                        contentDescription = "Download"
                    )
                }
            }
        }
    }
}