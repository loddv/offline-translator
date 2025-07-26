package dev.davidv.translator

import android.net.Uri

sealed class TranslatorMessage {
    data class TextInput(val text: String) : TranslatorMessage()
    data class FromLang(val language: Language) : TranslatorMessage()
    data class ToLang(val language: Language) : TranslatorMessage()
    data class SetImageUri(val uri: Uri) : TranslatorMessage()
    object SwapLanguages : TranslatorMessage()
    object ClearImage : TranslatorMessage()
    data class InitializeLanguages(val from: Language, val to: Language) : TranslatorMessage()
}