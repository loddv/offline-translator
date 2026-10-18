package dev.davidv.translator.assistantOverlay

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dev.davidv.translator.TranslationCoordinator
import dev.davidv.translator.TranslatorApplication

class TranslatorVoiceInteractionSessionService : VoiceInteractionSessionService() {
  private lateinit var app: TranslatorApplication
  private lateinit var translationCoordinator: TranslationCoordinator

  override fun onCreate() {
    super.onCreate()
    app = application as TranslatorApplication
    app.ensureServicesInitialized()
    translationCoordinator =
      TranslationCoordinator(app.translationService, app.speechService, app.languageDetector, app.imageProcessor, app.settingsManager)
  }

  override fun onNewSession(args: Bundle?): VoiceInteractionSession =
    TranslatorVoiceInteractionSession(
      context = this,
      settingsManager = app.settingsManager,
      translationCoordinator = translationCoordinator,
      catalogRepository = app.catalogRepository,
      languageMetadataManager = app.languageMetadataManager,
    )
}
