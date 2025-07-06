package com.example.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bergamot.DetectionResult
import com.example.bergamot.LangDetect
import com.example.bergamot.NativeLib
import com.example.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis


class MainActivity : ComponentActivity() {
    private var textToTranslate: String = ""
    private var detectedLanguage: Language? = null
    private lateinit var ocrService: OCRService
    private var onOcrProgress: (Float) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        ocrService = OCRService(this) { progress ->
            onOcrProgress(progress)
        }

        setContent {
            TranslatorTheme {
                MaterialTheme {
                    TranslatorApp(configForLang = { from, to ->
                        configForLang(
                            baseContext, from, to
                        )
                    },
                        initialText = textToTranslate,
                        detectedLanguage = detectedLanguage,
                        ocrService = ocrService,
                        onOcrProgress = { callback -> onOcrProgress = callback })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrService.cleanup()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = if (intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                textToTranslate = text ?: ""
                detectLanguageForSharedText(textToTranslate)
            }

            Intent.ACTION_SEND -> {
                textToTranslate = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                detectLanguageForSharedText(textToTranslate)
            }
        }
    }

    private fun detectLanguageForSharedText(text: String) {
        if (text.isNotEmpty()) {
            val ld = LangDetect()
            val detected = ld.detectLanguage(text)
            if (detected.isReliable) {
                detectedLanguage = Language.entries.firstOrNull { it.code == detected.language }
                println("detected ${detectedLanguage}")
            }
        }
    }

    companion object {
        fun createProcessTextIntent(context: Context, text: String): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_PROCESS_TEXT
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                type = "text/plain"
            }
        }
    }
}

@Composable
fun TranslationResult(
    context: Context, text: String, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp / 2),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                SelectionContainer(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp - 66).dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard: ClipboardManager? =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                        val clip = ClipData.newPlainText("Translated text", text)
                        clipboard?.setPrimaryClip(clip)
                    }, modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.copy),
                        contentDescription = "Copy translation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    configForLang: (Language, Language) -> String,
    onManageLanguages: () -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    output: String,
    onOutputChange: (String) -> Unit,
    from: Language,
    onFromChange: (Language) -> Unit,
    to: Language,
    onToChange: (Language) -> Unit,
    ocrService: OCRService,
    onOcrProgress: ((Float) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Move progress state to this composable
    var ocrProgress by remember { mutableFloatStateOf(0f) }

    // Set up progress callback once
    LaunchedEffect(Unit) {
        onOcrProgress { progress ->
            ocrProgress = progress
        }
    }

    println("from greeting, from=$from, to=$to")
    var detectedInput: DetectionResult? by remember { mutableStateOf(null) }
    val (isTranslating, setTranslating) = remember { mutableStateOf(false) }

    var translateImage: Bitmap? by remember { mutableStateOf(null) }
    val (ocrInProgress, setOcrInProgress) = remember { mutableStateOf(false) }


    if (input.isNotEmpty()) {
        val ld = LangDetect()
        val detected = ld.detectLanguage(input)
        detectedInput = if (detected.isReliable) {
            detected
        } else {
            null
        }
    }


    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                println("setting image")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    setOcrInProgress(true)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    translateImage = bitmap

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val text = ocrService.extractText(bitmap)
                            onInputChange(text)
                            setTranslating(true)
                            val translatedText = translateInForeground(from, to, context, text)
                            onOutputChange(translatedText)
                            setTranslating(false)
                            setOcrInProgress(false)
                        }
                    }
                }
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    // Track available language pairs
    val availableLanguages = remember { mutableStateMapOf<String, Boolean>() }

    // Check which language pairs are downloaded
    LaunchedEffect(Unit) {
        availableLanguages[Language.ENGLISH.code] = true
        withContext(Dispatchers.IO) {
            Language.entries.forEach { fromLang ->
                val toLang = Language.ENGLISH
                if (fromLang != toLang) {
                    val isAvailable = checkLanguagePairFiles(context, fromLang, toLang)
                    availableLanguages[fromLang.code] = isAvailable
                }
            }

            // Set initial languages if none selected - only if current languages aren't available
            if (availableLanguages[from.code] != true) {
                // Find first available language pair
                val availableFromLang = Language.entries.find { fromLang ->
                    availableLanguages[fromLang.code] == true && fromLang != Language.ENGLISH
                }

                if (availableFromLang != null) {
                    onToChange(Language.ENGLISH)
                    onFromChange(availableFromLang)
                }
            }
        }
    }

    // Check if any languages are available
    val hasAnyLanguages = availableLanguages.values.any { it }

    Scaffold(modifier = Modifier.fillMaxSize(), floatingActionButton = {
        FloatingActionButton(onClick = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Icon(
                painterResource(id = R.drawable.add_photo),
                contentDescription = "Translate image",
            )
        }
    }) { paddingValues ->

        if (!hasAnyLanguages) {
            // Show message when no languages are available
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Language Packs Installed",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Download language packs to start translating",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onManageLanguages
                ) {
                    Text("Download Languages")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language dropdown
                    var fromExpanded by remember { mutableStateOf(false) }
                    var toExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = fromExpanded,
                        onExpandedChange = { fromExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = from.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(IntrinsicSize.Min),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,  // smaller than default
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when focused
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when unfocused
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,  // Keep text clear even when disabled
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Add these to ensure good contrast for the text field background
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )

                        )
                        ExposedDropdownMenu(
                            expanded = fromExpanded,
                            onDismissRequest = { fromExpanded = false }) {
                            // Path exists to target
                            Language.entries.filter { x -> x != to && x != from && availableLanguages[x.code] == true }
                                .forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language.displayName) },
                                        onClick = {
                                            onFromChange(language)
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val translatedText = translateInForeground(
                                                        language,
                                                        to,
                                                        context,
                                                        input
                                                    )
                                                    onOutputChange(translatedText)
                                                    setTranslating(false)
                                                }
                                            }
                                            fromExpanded = false
                                        })
                                }
                        }

                    }
                    IconButton(onClick = {
                        val oldFrom = from
                        val oldTo = to
                        onFromChange(oldTo)
                        onToChange(oldFrom)

                        if (!isTranslating) {
                            setTranslating(true)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val translatedText =
                                        translateInForeground(oldTo, oldFrom, context, input)
                                    onOutputChange(translatedText)
                                    setTranslating(false)
                                }
                            }
                        }
                    }) {
                        Icon(
                            painterResource(id = R.drawable.compare),
                            contentDescription = "Reverse translation direction"
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = toExpanded,
                        onExpandedChange = { toExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = to.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(IntrinsicSize.Min),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,  // smaller than default
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when focused
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when unfocused
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,  // Keep text clear even when disabled
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Add these to ensure good contrast for the text field background
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = toExpanded,
                            onDismissRequest = { toExpanded = false }) {
                            Language.entries.filter { x -> x != from && x != to && availableLanguages[x.code] == true }
                                .forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language.displayName) },
                                        onClick = {
                                            onToChange(language)

                                            if (!isTranslating) {
                                                setTranslating(true)
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val translatedText = translateInForeground(
                                                            from, language, context, input
                                                        )
                                                        onOutputChange(translatedText)
                                                        setTranslating(false)
                                                    }
                                                }
                                            }
                                            toExpanded = false
                                        })
                                }
                        }
                    }
                    IconButton(onClick = onManageLanguages) {
                        Icon(
                            painterResource(id = R.drawable.settings),
                            contentDescription = "Manage Languages"
                        )
                    }

                }
                Spacer(modifier = Modifier.height(8.dp))


                if (translateImage != null && ocrInProgress) {
                    LinearProgressIndicator(
                        progress = { ocrProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Image(
                        bitmap = translateImage!!.asImageBitmap(),
                        contentDescription = "Image to translate"
                    )
                }
                // Input TextField
                OutlinedTextField(
                    value = input,
                    onValueChange = { newInput ->
                        onInputChange(newInput)
                        val ld = LangDetect()

                        val detected = ld.detectLanguage(newInput)
                        println("onchange detected $detected")
                        detectedInput = if (detected.isReliable) {
                            detected
                        } else {
                            null
                        }
                        if (!isTranslating) {
                            setTranslating(true)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val translatedText =
                                        translateInForeground(from, to, context, newInput)
                                    onOutputChange(translatedText)
                                    setTranslating(false)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(120.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Enter text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when focused
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,  // Clear text when unfocused
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,  // Keep text clear even when disabled
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Add these to ensure good contrast for the text field background
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    val autoLang =
                        Language.entries.firstOrNull { l -> l.code == detectedInput?.language }

                    if (detectedInput != null && autoLang != null && autoLang != from) {
                        Button(
                            onClick = {
                                onFromChange(autoLang)
                                val actualTo = if (to == autoLang) {
                                    onToChange(Language.ENGLISH)
                                    Language.ENGLISH
                                } else {
                                    to
                                }

                                setTranslating(true)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val translatedText = translateInForeground(
                                            autoLang,
                                            actualTo,
                                            context,
                                            input
                                        )
                                        onOutputChange(translatedText)
                                        setTranslating(false)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RectangleShape,
                        ) {
                            Text(
                                "From ${autoLang.displayName} ✨"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }


                if (output.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TranslationResult(
                        context = context, text = output, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } // Close the else block

    }
}

fun translateInForeground(
    from: Language, to: Language, context: Context, input: String
): String {
    println("Called translate $from -> $to")
    if (from == to) {
        return input
    }

    val nl = NativeLib()

    var output: String
    var pairs = emptyList<Pair<Language, Language>>()

    if (from != Language.ENGLISH && to != Language.ENGLISH) {
        pairs = pairs + Pair(from, Language.ENGLISH)
        if (!checkLanguagePairFiles(context, from, Language.ENGLISH)) {
            return "Language $pairs not installed"
        }
        pairs = pairs + Pair(Language.ENGLISH, to)
        if (!checkLanguagePairFiles(context, Language.ENGLISH, to)) {
            return "Language $pairs not installed"
        }
    } else {
        if (!checkLanguagePairFiles(context, from, to)) {
            return "Language not installed"
        }
        pairs = pairs + Pair(from, to)
    }

    val elapsed = measureTimeMillis {
        var intermediateOut = ""
        var intermediateIn = input
        // TODO use pivot instead of this
        pairs.forEach({ pair ->
            println("Translating ${pair}")
            val cfg = configForLang(context, pair.first, pair.second)
            intermediateOut =
                nl.stringFromJNI(cfg, intermediateIn, "${pair.first.code}${pair.second.code}")
            intermediateIn = intermediateOut
        })
        output = intermediateOut

    }
    println("Took ${elapsed}ms to translate")
    return output

}

@Preview(
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GreetingPreview() {
    // Preview can't use real OCR service, create a mock
    TranslatorTheme {
        // Preview disabled - requires OCR service
        Text("Preview requires OCR service")
    }
}

fun configForLang(context: Context, fromLang: Language, toLang: Language): String {
    val dataPath = File(context.filesDir, "bin")
    val (model, vocab, lex) = filesFor(fromLang, toLang)
    val cfg = """
models:
  - ${dataPath}/${model}
vocabs:
  - ${dataPath}/${vocab}
  - ${dataPath}/${vocab}
shortlist:
    - ${dataPath}/${lex}
    - false
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 0
quiet: false
quiet-translation: false
gemm-precision: int8shiftAlphaAll
alignment: soft
)"""
    return cfg
}