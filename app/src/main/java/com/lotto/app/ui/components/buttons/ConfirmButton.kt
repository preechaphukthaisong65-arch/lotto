package com.lotto.app.ui.components.buttons

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Shared action button that asks for confirmation before invoking onConfirm. */
@Composable
fun ConfirmButton(
    onConfirm: () -> Unit,
    label: String = "บันทึก",
    confirmationTitle: String = "ยืนยันการทำรายการ",
    confirmationMessage: String = "คุณต้องการดำเนินการนี้ใช่หรือไม่?",
    confirmLabel: String = "ยืนยัน",
    cancelLabel: String = "ยกเลิก",
    loadingLabel: String = "กำลังดำเนินการ...",
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    var showConfirmation by remember { mutableStateOf(false) }

    Button(
        onClick = { showConfirmation = true },
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Text(text = loadingLabel)
        } else {
            Text(text = label)
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(text = confirmationTitle) },
            text = { Text(text = confirmationMessage) },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(text = cancelLabel)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        onConfirm()
                    },
                    enabled = enabled && !isLoading
                ) {
                    Text(text = confirmLabel)
                }
            }
        )
    }
}
