package com.example.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun TranslationField(
    text: String,
    onTextChange: ((String) -> Unit)? = null,
    placeholder: String? = null,
    isInput: Boolean = true,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    minHeight: Int = 120
) {
    val context = LocalContext.current
    
    Column(modifier = modifier) {
        if (isInput) {
            StyledTextField(
                text = text,
                onValueChange = onTextChange ?: {},
                placeholder = placeholder,
                textStyle = textStyle,
                minHeight = minHeight,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Output field with copy functionality and transparent background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight.dp, max = LocalConfiguration.current.screenHeightDp.dp / 2)
            ) {
                if (text.isNotEmpty()) {
                    // Text content with scrolling
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                            .padding(end = 40.dp) // Leave space for copy button
                    ) {
                        Text(
                            text = text,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Copy button positioned sticky to the right
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Translation", text)
                            clipboard.setPrimaryClip(clip)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            painterResource(id = R.drawable.copy),
                            contentDescription = "Copy translation",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                // No placeholder when empty - just empty space
            }
        }
    }
}