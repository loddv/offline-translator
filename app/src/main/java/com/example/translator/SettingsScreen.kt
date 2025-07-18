package com.example.translator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.translator.ui.theme.TranslatorTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    availableLanguages: List<Language>,
    onSettingsChange: (AppSettings) -> Unit,
    onManageLanguages: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Languages Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Languages",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Manage Languages Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Language Packs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedButton(
                            onClick = onManageLanguages
                        ) {
                            Text("Manage Languages")
                        }
                    }
                }
            }

            // General Settings Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "General",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Default Target Language
                    var languageExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = "Default Target Language",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = settings.defaultTargetLanguage.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            availableLanguages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.displayName) },
                                    onClick = {
                                        onSettingsChange(settings.copy(defaultTargetLanguage = language))
                                        languageExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Translation Models Base URL
                    Text(
                        text = "Base URL for Translation Models",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = settings.translationModelsBaseUrl,
                        onValueChange = { 
                            onSettingsChange(settings.copy(translationModelsBaseUrl = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Tesseract Models Base URL
                    Text(
                        text = "Base URL for Tesseract Models",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = settings.tesseractModelsBaseUrl,
                        onValueChange = { 
                            onSettingsChange(settings.copy(tesseractModelsBaseUrl = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            
            // Images Settings Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Images",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Background Mode
                    var backgroundModeExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = "Background Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = backgroundModeExpanded,
                        onExpandedChange = { backgroundModeExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = settings.backgroundMode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backgroundModeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = backgroundModeExpanded,
                            onDismissRequest = { backgroundModeExpanded = false }
                        ) {
                            BackgroundMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName) },
                                    onClick = {
                                        onSettingsChange(settings.copy(backgroundMode = mode))
                                        backgroundModeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TranslatorTheme {
        SettingsScreen(
            settings = AppSettings(),
            availableLanguages = Language.entries,
            onSettingsChange = {},
            onManageLanguages = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SettingsScreenDarkPreview() {
    TranslatorTheme {
        SettingsScreen(
            settings = AppSettings(),
            availableLanguages = Language.entries,
            onSettingsChange = {},
            onManageLanguages = {}
        )
    }
}