package com.example.translator

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.translator.ui.theme.TranslatorTheme
//import com.example.bergamot.NativeClass
import com.example.bergamot.NativeLib
import java.io.File
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val base = "https://storage.googleapis.com/bergamot-models-sandbox/0.3.1"
        val lang = "esen"
        val model =  "model.esen.intgemm.alphas.bin"
        val vocab = "vocab.esen.spm"
        val lex = "lex.50.50.esen.s2t.bin"
        val files = arrayOf(
          model, vocab,

            lex, // !! enes not esen
        )
        val dataPath = baseContext.getExternalFilesDir("bin")!!

        files.forEach { f ->

            val file = File(dataPath.absolutePath + "/" + f)
            if (!file.exists()) {
                val dm = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse("${base}/${lang}/${f}"))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setTitle("Downloading ${f}")
                val out = Uri.fromFile(file)
                println("xxDownloading ${f}, ${file} = ${out}")

                request.setDestinationUri(out)
                val downloadReference = dm.enqueue(request) ?: 0

            } else {
                println("Not downloading ${f}")
            }
        }

        println("listed ${dataPath.listFiles().joinToString()}")
        val cfg = """
auto cfg = R"(bergamot-mode: native
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
        val input =
            """Continúan los cambios en la Agencia de Recaudación y Control Aduanero (ARCA), ex-AFIP. A través del decreto 13/2025 publicado este martes a la madrugada en el Boletín Oficial, el Gobierno decidió avanzar en la reducción del sueldo del director ejecutivo del organismo y del resto de directores generales, tal y como se había anticipado cuando se renombró al ente recaudador de impuestos, y además que se llevará adelante una “reducción de la estructura inferior”.
                
La medida había sido anticipada por el vocero presidencial, Manuel Adorni, a última hora del lunes, quien aseguró que con esta decisión “se terminan los privilegios y el gasto desenfrenado en ARCA”. El anuncio ocurrió horas después de que LA NACION publicara que el director de la Dirección General Impositiva (DGI), Andrés Vázquez, había ascendido a su pareja, María Eugenia Fanelli, a un puesto jerárquico con más del doble del sueldo.
"""
"""
    “El Presidente de la Nación acaba de firmar el decreto que determina que desde hoy se reduce el sueldo del Director Ejecutivo y los directores generales en un 80%”, indicó el funcionario a cargo del área de comunicación del Gobierno. En el artículo 4 del decreto se especificó que: “El Director Ejecutivo de ARCA percibirá una remuneración equivalente a la de un ministro”. Mientras que en el 5 se hizo lo propio con los directores generales: “Percibirán una remuneración equivalente a la de un Secretario de Estado”.

En ese sentido, indicó que además “se dispuso una reducción del presupuesto del ARCA destinado a la cuenta de jerarquización del 0,65% al 0,60%, generando un ahorro de 121.000 millones de pesos correspondientes a sueldos de funcionarios durante este 2025″.

“Por último, el Poder Ejecutivo instó a las autoridades del ARCA a que avancen con una reducción de la estructura inferior, estimando que esta debe situarse en torno al 45%”, especificó el vocero en X sobre el decreto que lleva la firma de Milei, Guillermo Francos, Luis Caputo y Federico Sturzenegger.

Las tres decisiones adoptadas por el Gobierno se corresponden con los planes iniciales que se tenían para ARCA cuando esta reemplazó a la Administración Federal de Ingresos Públicos (AFIP), con lo que buscaban simplificar y optimizar su operabilidad.

En ese momento, el Gobierno sostuvo que el mayor cambio que traía esa decisión era en términos de personal. De acuerdo con el Ejecutivo, se ahorrarían alrededor de ${'$'}6400 millones, al reducir las autoridades superiores en un 45% y los niveles inferiores de la estructura actual en un 35%.
"""
        val nl = NativeLib()
        val output: String
        val elapsed = measureTimeMillis {
            output = nl.stringFromJNI(cfg, input)
        }
        println("Translation took ${elapsed}")
        setContent {
            TranslatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
//                        name = "commented out",

                        name = "IN\n" + input + "\nOUT\n" + output,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "$name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TranslatorTheme {
        Greeting("Androasdid")
    }
}