package com.example.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TranslatorTheme {
                MaterialTheme {
                    TranslatorApp(
                        configForLang = { from, to ->
                            configForLang(
                                baseContext, from, to
                            )
                        },
                        initialText = textToTranslate
                    )
                }
            }
        }
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
            }

            Intent.ACTION_SEND -> {
                textToTranslate = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
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
    context: Context,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth()
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
                        modifier = Modifier.widthIn(max=(LocalConfiguration.current.screenWidthDp - 66).dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard: ClipboardManager? =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                        val clip = ClipData.newPlainText("Translated text", text)
                        clipboard?.setPrimaryClip(clip)
                    },
                    modifier = Modifier.size(24.dp)
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
    initialText: String?
) {
    val (from, setFrom) = remember { mutableStateOf(Language.SPANISH) }
    val (to, setTo) = remember { mutableStateOf(Language.ENGLISH) }

    var input by remember { mutableStateOf(initialText ?: "") }
    var output by remember { mutableStateOf("") }
    var detectedInput: DetectionResult? by remember { mutableStateOf(null) }
    val (isTranslating, setTranslating) = remember { mutableStateOf(false) }

    if (initialText !== null && initialText != "") {
        val ld = LangDetect()

        val detected = ld.detectLanguage(input)
        detectedInput = if (detected.isReliable) {
            detected
        } else {
            null
        }
    }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
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

            // Set initial languages if none selected
            // Try to find first available pair
            Language.entries.forEach { fromLang ->
                if (availableLanguages[fromLang.code] == true && fromLang != Language.ENGLISH) {
                    setFrom(fromLang)
                    setTo(Language.ENGLISH)
                    return@withContext
                }

            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->

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
                    ExposedDropdownMenu(expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false }) {
                        // Path exists to target
                        Language.entries
                            .filter { x -> x != to && x != from && availableLanguages[x.code] == true }
                            .forEach { language ->
                                DropdownMenuItem(text = { Text(language.displayName) }, onClick = {
                                    setFrom(language)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            output = translateInForeground(language, to, context, input)
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
                    setFrom(oldTo)
                    setTo(oldFrom)

                    if(!isTranslating) {
                        setTranslating(true)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                output = translateInForeground(oldTo, oldFrom, context, input)
                                setTranslating(false)
                            }
                        }
                    }
                }) {
                    Icon(painterResource(id = R.drawable.compare), contentDescription = "Reverse translation direction")
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

                    ExposedDropdownMenu(expanded = toExpanded,
                        onDismissRequest = { toExpanded = false }) {
                        Language.entries
                            .filter { x -> x != from && x != to && availableLanguages[x.code] == true }
                            .forEach { language ->
                                DropdownMenuItem(text = { Text(language.displayName) }, onClick = {
                                    setTo(language)

                                    if(!isTranslating) {
                                        setTranslating(true)
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                output = translateInForeground(from, language, context, input)
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
                    Icon(painterResource(id = R.drawable.settings), contentDescription = "Manage Languages")
                }

            }
            Spacer(modifier = Modifier.height(8.dp))


            // Input TextField
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    val ld = LangDetect()

                    val detected = ld.detectLanguage(input)
                    println("detected $detected")
                    detectedInput = if (detected.isReliable) {
                        detected
                    } else {
                        null
                    }
                    if(!isTranslating) {
                        setTranslating(true)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                output = translateInForeground(from, to, context, input)
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
                            setFrom(autoLang)
                            setTranslating(true)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    output = translateInForeground(autoLang, to, context, input)
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
                    context = context,
                    text = output,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        }
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
        pairs.forEach({ pair ->
            println("Translating ${pair}")
            val cfg = configForLang(context, pair.first, pair.second)
            intermediateOut = nl.stringFromJNI(cfg, intermediateIn, "${pair.first.code}${pair.second.code}")
            intermediateIn = intermediateOut
        })
        output = intermediateOut

    }
    println("Took ${elapsed}ms to translate")
    return output

}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GreetingPreview() {
    TranslatorTheme {
        Greeting({ x, y -> "" }, {}, "")
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