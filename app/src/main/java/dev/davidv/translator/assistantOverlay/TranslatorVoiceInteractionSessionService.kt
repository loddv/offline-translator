package dev.davidv.translator.assistantOverlay

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dev.davidv.translator.TranslatorApplication

class TranslatorVoiceInteractionSessionService : VoiceInteractionSessionService() {
  private lateinit var app: TranslatorApplication

  override fun onCreate() {
    super.onCreate()
    app = application as TranslatorApplication
    app.ensureServicesInitialized()
  }

  override fun onNewSession(args: Bundle?): VoiceInteractionSession =
    TranslatorVoiceInteractionSession(
      context = this,
      settingsManager = app.settingsManager,
      translationCoordinator = app.translationCoordinator,
      catalogRepository = app.catalogRepository,
      languageMetadataManager = app.languageMetadataManager,
    )
}
