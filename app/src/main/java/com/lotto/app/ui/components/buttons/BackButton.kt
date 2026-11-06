package com.lotto.app.ui.components.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Shared back button. Navigation is handled by the calling screen. */
@Composable
fun BackButton(
    onBack: () -> Unit,
    label: String = "ย้อนกลับ",
    enabled: Boolean = true
) {
    TextButton(onClick = onBack, enabled = enabled) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null
        )
        Text(text = label)
    }
}
