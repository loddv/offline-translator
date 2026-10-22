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

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import dev.davidv.translator.adblock.AdblockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TranslatorApplication : Application() {
  lateinit var settingsManager: SettingsManager
  lateinit var languageMetadataManager: LanguageMetadataManager
  lateinit var filePathManager: FilePathManager
  lateinit var catalogRepository: CatalogRepository
  lateinit var downloadManager: DownloadManager
  lateinit var imageProcessor: ImageProcessor
  lateinit var translationService: TranslationService
  lateinit var speechService: SpeechService
  lateinit var languageDetector: LanguageDetector
  lateinit var translationCoordinator: TranslationCoordinator
  lateinit var adblockManager: AdblockManager
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var servicesInitialized = false

  override fun onCreate() {
    super.onCreate()
    installIsrgRootOnLegacyAndroid(this)

    // The assistant VoiceInteractionService is directBootAware, so the system starts
    // this process before the user unlocks. Service init below touches credential-encrypted
    // SharedPreferences, which throw until unlock — so defer it until the user is unlocked.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isUserUnlocked()) {
      Log.d("TranslatorApplication", "User locked (direct boot); deferring service init until unlock")
      registerUnlockReceiver()
      return
    }

    initializeServices()
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private fun isUserUnlocked(): Boolean = getSystemService(UserManager::class.java).isUserUnlocked

  private fun registerUnlockReceiver() {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context,
          intent: Intent,
        ) {
          Log.d("TranslatorApplication", "User unlocked; running deferred service init")
          ensureServicesInitialized()
          unregisterReceiver(this)
        }
      }
    registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
  }

  /** Services started in a process that came up during direct boot can be created after
   *  unlock but before ACTION_USER_UNLOCKED reaches [registerUnlockReceiver], so they call
   *  this before touching the shared instances instead of racing the broadcast. */
  fun ensureServicesInitialized() {
    check(Build.VERSION.SDK_INT < Build.VERSION_CODES.N || isUserUnlocked()) { "Application services require an unlocked user" }
    initializeServices()
  }

  private fun initializeServices() {
    if (servicesInitialized) {
      return
    }
    servicesInitialized = true
    Log.d("TranslatorApplication", "Initializing application services")

    settingsManager = SettingsManager(this)
    filePathManager = FilePathManager(this, settingsManager.settings)
    catalogRepository = CatalogRepository.open(this, filePathManager)
    launchAppEffects(appScope, this, settingsManager, catalogRepository)

    val languagesFlow =
      catalogRepository.catalog
        .map { it.languageList }
        .stateIn(appScope, SharingStarted.Eagerly, catalogRepository.catalog.value.languageList)
    languageMetadataManager = LanguageMetadataManager(this, languagesFlow)
    imageProcessor = ImageProcessor(this, catalogRepository)
    translationService = TranslationService(settingsManager, filePathManager, catalogRepository)
    speechService = SpeechService(settingsManager, catalogRepository)
    languageDetector = LanguageDetector { code -> catalogRepository.catalog.value.languageByCode(code) }
    translationCoordinator =
      TranslationCoordinator(translationService, speechService, languageDetector, imageProcessor, settingsManager)
    adblockManager = AdblockManager(filePathManager)
    downloadManager = DownloadManager(this, appScope, catalogRepository, filePathManager, settingsManager, adblockManager)
    appScope.launch {
      downloadManager.state
        .map { foregroundDownloads(it) != null }
        .distinctUntilChanged()
        .filter { it }
        .collect { DownloadService.start(this@TranslatorApplication) }
    }
  }
}
