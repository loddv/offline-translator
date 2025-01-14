package com.example.translator

import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
//import com.example.bergamot.NativeClass
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bergamot.NativeLib
import com.example.translator.MainActivity.Language
import com.example.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis


class MainActivity : ComponentActivity() {
    enum class Language(val code: String, val displayName: String) {
        ENGLISH("en", "English"),
        BULGARIAN("bg", "Bulgarian"),
        CATALAN("ca", "Catalan"),
        CZECH("cs", "Czech"),
        DANISH("da", "Danish"),
        GERMAN("de", "German"),
        GREEK("el", "Greek"),
        SPANISH("es", "Spanish"),
        ESTONIAN("et", "Estonian"),

        //        FINNISH("fi", "Finnish"),
        FRENCH("fr", "French"),
        CROATIAN("hr", "Croatian"),
        INDONESIAN("id", "Indonesian"),
        ITALIAN("it", "Italian"),
        DUTCH("nl", "Dutch"),
        POLISH("pl", "Polish"),
        PORTUGUESE("pt", "Portuguese"),
        ROMANIAN("ro", "Romanian"),
        SLOVENIAN("sl", "Slovenian"),
        SWEDISH("sv", "Swedish"),
        TURKISH("tr", "Turkish")
    }


    private fun filesFor(fromLang: Language, toLang: Language): Triple<String, String, String> {
        val lang = "${fromLang.code}${toLang.code}"
        // vocab lang is always *en
        val vocabLang = if (fromLang == Language.ENGLISH) {
            "${toLang.code}${fromLang.code}"
        } else {
            "${fromLang.code}${toLang.code}"
        }
        val model = "model.$lang.intgemm.alphas.bin"
        val vocab = "vocab.$vocabLang.spm"
        val lex = "lex.50.50.$lang.s2t.bin"
        return Triple(model, vocab, lex)
    }

    private fun configForLang(fromLang: Language, toLang: Language): String {
        val dataPath = baseContext.getExternalFilesDir("bin")!!
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TranslatorTheme {
                MaterialTheme {
                    TranslatorApp(configForLang = { from, to -> this.configForLang(from, to) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(configForLang: (Language, Language) -> String,
             onManageLanguages: () -> Unit,
) {
    val (from, setFrom) = remember { mutableStateOf(Language.SPANISH) }
    val (to, setTo) = remember { mutableStateOf(Language.ENGLISH) }

    var input by remember { mutableStateOf("Continúan los cambios en la Agencia de Recaudación y Control Aduanero (ARCA), ex-AFIP. A través del decreto 13/2025 publicado este martes a la madrugada en el Boletín Oficial, el Gobierno decidió avanzar en la reducción del sueldo del director ejecutivo del organismo y del resto de directores generales, tal y como se había anticipado cuando se renombró al ente recaudador de impuestos, y además que se llevará adelante una “reducción de la estructura inferior”.\n") }
    var output by remember { mutableStateOf("") }
    val (isTranslating, setTranslating) = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val nl = NativeLib()

    val context = LocalContext.current
    // Track available language pairs
    val availableLanguages = remember { mutableStateMapOf<String, Boolean>() }

    // Check which language pairs are downloaded
    LaunchedEffect(Unit) {
        availableLanguages[Language.ENGLISH.code] = true
        withContext(Dispatchers.IO) {
            Language.values().forEach { fromLang ->
                val toLang = Language.ENGLISH
                    if (fromLang != toLang) {
                        val isAvailable = checkLanguagePairFiles(context, fromLang, toLang)
                        availableLanguages[fromLang.code] = isAvailable
                    }
                }

            // Set initial languages if none selected
                // Try to find first available pair
                Language.values().forEach { fromLang ->
                    if (availableLanguages[fromLang.code] == true) {
                        setFrom(fromLang)
                        setTo(Language.ENGLISH)
                        return@withContext
                    }

            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = fromExpanded,
                    onDismissRequest = { fromExpanded = false }
                ) {
                    // Path exists to target
                    MainActivity.Language.values().filter { x -> x != to && x != from && availableLanguages[x.code] == true }
                        .forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.displayName) },
                                onClick = {
                                    setFrom(language)
                                    fromExpanded = false
                                }
                            )
                        }
                }
            }
            Text("to")
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
                    modifier = Modifier.menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = toExpanded,
                    onDismissRequest = { toExpanded = false }
                ) {
                    MainActivity.Language.values().filter { x -> x != from && x != to && availableLanguages[x.code] == true }
                        .forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.displayName) },
                                onClick = {
                                    setTo(language)
                                    toExpanded = false
                                }
                            )
                        }
                }
            }
            IconButton(onClick = onManageLanguages) {
                Icon(Icons.Default.Settings, contentDescription = "Manage Languages")
            }

        }
        Spacer(modifier = Modifier.height(8.dp))


        // Input TextField
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .height(120.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text ("Enter text") }
        )

        Spacer(modifier = Modifier.height(8.dp))


        Button(
            onClick = {
                setTranslating(true)
                var pairs = emptyList<Pair<Language, Language>>();
                if (from != Language.ENGLISH && to != Language.ENGLISH) {
                    pairs = pairs + Pair(from, Language.ENGLISH)
                    pairs = pairs + Pair(Language.ENGLISH, to)
                } else {
                    pairs = pairs + Pair(from, to)
                }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val elapsed = measureTimeMillis {
                                var intermediateOut = ""
                                var intermediateIn = input
                                pairs.forEach({ pair ->
                                    val cfg = configForLang(pair.first, pair.second)
                                    intermediateOut = nl.stringFromJNI(cfg, intermediateIn)
                                    intermediateIn = intermediateOut
                                })
                                output = intermediateOut

                            }
                            println("Took $elapsed to translate")
                        } finally {
                            setTranslating(false)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotEmpty() && !isTranslating
        ) {
            if (isTranslating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Translate")
            }
        }


        if (output.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            // Output TextField (read-only)
            OutlinedTextField(
                value = output,
                onValueChange = { }, // Read-only
//                readOnly = true, // can't copy if readonly
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TranslatorTheme {
        Greeting( {x,y -> ""}, {})
    }
}

