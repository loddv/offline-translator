package dev.davidv.translator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.theme.TranslatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoLanguagesScreen(
    onLanguageDownloaded: () -> Unit,
    onLanguageDeleted: () -> Unit,
    onDone: () -> Unit,
    hasLanguages: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Language Setup") }
            )
        },
        bottomBar = {
            Button(
                onClick = onDone,
                enabled = hasLanguages,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Done")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Download language packs to start translating",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            LanguageManagerScreen(
                onLanguageDownloaded = onLanguageDownloaded,
                onLanguageDeleted = onLanguageDeleted,
                embedded = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NoLanguagesScreenPreview() {
    TranslatorTheme {
        NoLanguagesScreen(
            onLanguageDownloaded = {},
            onLanguageDeleted = {},
            onDone = {},
            hasLanguages = false
        )
    }
}