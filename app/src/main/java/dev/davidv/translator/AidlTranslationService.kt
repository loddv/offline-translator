package dev.davidv.translator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AidlTranslationService : Service() {
  private val tag = this.javaClass.name.substringAfterLast('.')

  private lateinit var settingsManager: SettingsManager
  private lateinit var catalogRepository: CatalogRepository
  private lateinit var translationCoordinator: TranslationCoordinator

  // Callbacks are binder calls into the client; keep them off the main thread.
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    val app = application as TranslatorApplication
    app.ensureServicesInitialized()
    settingsManager = app.settingsManager
    catalogRepository = app.catalogRepository
    translationCoordinator = app.translationCoordinator
    Log.d(tag, "onCreate")
  }

  override fun onBind(intent: Intent?): IBinder {
    Log.d(tag, "onBind")
    return binder
  }

  private val binder =
    object : ITranslationService.Stub() {
      override fun translate(
        textToTranslate: String?,
        fromLanguageStr: String?,
        toLanguageStr: String?,
        callback: ITranslationCallback?,
      ) {
        Log.d(tag, "txt len:${textToTranslate?.length ?: -1}, from:$fromLanguageStr, to:$toLanguageStr, cb = ${callback != null}")

        if (textToTranslate == null || callback == null) {
          Log.w(tag, "translate: textToTranslate or callback is null")
          return
        }

        val catalog = catalogRepository.catalog.value
        val fromLanguage = fromLanguageStr?.takeIf { it.isNotEmpty() }?.let { catalog.languageByCode(it) }
        val toLanguage = toLanguageStr?.takeIf { it.isNotEmpty() }?.let { catalog.languageByCode(it) }

        serviceScope.launch {
          val langs = LanguageAvailabilityState.of(catalog).sourceLanguages()
          val from = fromLanguage ?: translationCoordinator.detectLanguageRobust(textToTranslate, null, langs)
          Log.d(tag, "Detected lang $from")
          if (from == null) {
            Log.d(tag, "Could not detect language")
            val err =
              TranslationError().apply {
                type = ErrorType.COULD_NOT_DETECT_LANGUAGE
                language = null
                message = null
              }
            callback.onTranslationError(err)
            return@launch
          }
          if (!langs.contains(from)) {
            Log.d(tag, "Detected language ${from.displayName} not available")
            val err =
              TranslationError().apply {
                type = ErrorType.DETECTED_BUT_UNAVAILABLE
                language = from.displayName
                message = null
              }
            callback.onTranslationError(err)
            return@launch
          }
          val to = toLanguage ?: catalog.languageByCode(settingsManager.settings.value.defaultTargetLanguageCode)
          if (to == null) {
            val err =
              TranslationError().apply {
                type = ErrorType.UNEXPECTED
                language = null
                message = getString(R.string.aidl_target_unavailable)
              }
            callback.onTranslationError(err)
            return@launch
          }
          when (val result = translationCoordinator.translateText(from, to, textToTranslate)) {
            is TranslationResult.Success -> {
              val translatedText = result.result.translated
              Log.d(tag, "translated text: $translatedText")
              callback.onTranslationResult(translatedText)
            }

            is TranslationResult.Error -> {
              Log.d(tag, "Translation error: ${result.message}")
              val err =
                TranslationError().apply {
                  type = ErrorType.UNEXPECTED
                  language = null
                  message = result.message
                }
              callback.onTranslationError(err)
            }
          }
        }
      }
    }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    Log.d(tag, "onStartCommand received, but this service is meant to be bound.")
    return START_NOT_STICKY
  }

  override fun onUnbind(intent: Intent?): Boolean {
    Log.d(tag, "onUnbind")
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    Log.d(tag, "onDestroy")
    serviceScope.cancel()
    super.onDestroy()
  }
}
