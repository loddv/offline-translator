package com.example.translator

//import com.example.bergamot.NativeClass
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bergamot.NativeLib
import com.example.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.measureTimeMillis


class MainActivity : ComponentActivity() {

    private fun filesFor(fromLang: String, toLang: String): Triple<String, String, String> {
        val lang = "$fromLang$toLang"
        val model = "model.$lang.intgemm.alphas.bin"
        val vocab = "vocab.$lang.spm"
        val lex = "lex.50.50.$lang.s2t.bin"
        return Triple(model, vocab, lex)
    }

    private fun configForLang(fromLang: String, toLang: String): String {
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

    private fun ensureLocalFiles(fromLang: String, toLang: String) {
        val files = filesFor(fromLang, toLang)
        val lang = "$fromLang$toLang"
        val dataPath = baseContext.getExternalFilesDir("bin")!!
        val base = "https://storage.googleapis.com/bergamot-models-sandbox/0.3.1"

        files.toList().forEach { f ->

            val file = File(dataPath.absolutePath + "/" + f)
            println("Checking ${f} = ${file}")
            if (!file.exists()) {
                val dm = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse("${base}/${lang}/${f}"))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setTitle("Downloading ${f}")
                val out = Uri.fromFile(file)
                println("Downloading ${f}, ${file} = ${out}")

                request.setDestinationUri(out)
                val downloadReference = dm.enqueue(request) ?: 0

            } else {
                println("${f} present, not downloading")
            }
        }
        println("listed ${dataPath.listFiles().joinToString()}")

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fromLang = "es"
        val toLang = "en"
        ensureLocalFiles(fromLang, toLang)
        val cfg = configForLang(fromLang, toLang)

        setContent {
            TranslatorTheme {
                MaterialTheme {
                    Greeting(cfg)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(cfg: String) {
    var input by remember { mutableStateOf("Continúan los cambios en la Agencia de Recaudación y Control Aduanero (ARCA), ex-AFIP. A través del decreto 13/2025 publicado este martes a la madrugada en el Boletín Oficial, el Gobierno decidió avanzar en la reducción del sueldo del director ejecutivo del organismo y del resto de directores generales, tal y como se había anticipado cuando se renombró al ente recaudador de impuestos, y además que se llevará adelante una “reducción de la estructura inferior”.\n") }
    var output by remember { mutableStateOf("") }
    val translationState = remember { mutableStateOf(false) }
    var isTranslating by translationState

    val scope = rememberCoroutineScope()
    val nl = NativeLib()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Input TextField
        OutlinedTextField(

            value = input,
            onValueChange = { input = it },
            label = { Text("Enter text to translate") },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .height(120.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            enabled = !isTranslating
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isTranslating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Button(
                onClick = {
                    translationState.value = true

                    scope.launch {
                        try {
                            val elapsed = measureTimeMillis {
                                output = nl.stringFromJNI(cfg, input)
                            }
                            println("Took ${elapsed} to translate")
                        } finally {
                            translationState.value = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTranslating && input.isNotEmpty()
            ) {
                Text("Translate")
            }
        }

        if (output.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            // Output TextField (read-only)
            OutlinedTextField(
                value = output,
                onValueChange = { }, // Read-only
                label = { Text("Translation") },
                readOnly = true,
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
        Greeting("")
    }
}