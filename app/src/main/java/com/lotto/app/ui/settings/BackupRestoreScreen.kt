package com.lotto.app.ui.settings

import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lotto.app.data.backup.EncryptedBackupManager
import com.lotto.app.ui.components.buttons.BackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreen(
    manager: EncryptedBackupManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                try {
                    manager.exportEncrypted(uri, password.toCharArray())
                    password = ""
                    Toast.makeText(context, "สำรองข้อมูลแบบเข้ารหัสเรียบร้อย", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    error = e.message ?: "สำรองข้อมูลไม่สำเร็จ"
                } finally {
                    busy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreUri = uri
            showRestoreConfirmation = true
        }
    }

    Scaffold(
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp)) { BackButton(onBack = onBack) }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("สำรองและกู้คืนข้อมูล", style = MaterialTheme.typography.headlineSmall)
            Text(
                "ไฟล์สำรองจะเข้ารหัสด้วยรหัสผ่านที่คุณกำหนด ไม่ส่งข้อมูลลูกค้าหรือยอดเงินออกจากเครื่องโดยอัตโนมัติ",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("รหัสผ่านไฟล์สำรอง (อย่างน้อย 12 ตัวอักษร)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy
            )
            Button(
                onClick = { exportLauncher.launch("lotto-backup.lotto") },
                enabled = !busy && password.length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "กำลังดำเนินการ..." else "สำรองข้อมูลแบบเข้ารหัส") }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !busy && password.length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text("เลือกไฟล์เพื่อกู้คืนข้อมูล") }
            Text(
                "ข้อควรระวัง: หากลืมรหัสผ่านจะไม่สามารถกู้คืนไฟล์ได้ เก็บรหัสผ่านแยกจากโทรศัพท์ และทำไฟล์สำรองไว้ในที่ปลอดภัย การกู้คืนจะแทนที่ฐานข้อมูลปัจจุบันทั้งหมด",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showRestoreConfirmation && restoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false; restoreUri = null },
            title = { Text("ยืนยันกู้คืนข้อมูล") },
            text = {
                Text("การกู้คืนจะแทนที่ข้อมูลทั้งหมดในเครื่องด้วยข้อมูลในไฟล์สำรองปัจจุบัน และแอปจะปิดเพื่อเริ่มฐานข้อมูลใหม่ กรุณาตรวจสอบว่าเลือกไฟล์และรหัสผ่านถูกต้อง")
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && password.length >= 12,
                    onClick = {
                        val uri = restoreUri ?: return@TextButton
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                manager.restoreEncrypted(uri, password.toCharArray())
                                password = ""
                                Toast.makeText(context, "กู้คืนข้อมูลสำเร็จ แอปจะปิด กรุณาเปิดใหม่", Toast.LENGTH_LONG).show()
                                delay(900)
                                Process.killProcess(Process.myPid())
                            } catch (e: Exception) {
                                if (manager.requiresRestartForDatabaseReload) {
                                    Toast.makeText(context, "กู้คืนไม่สมบูรณ์ แอปจะปิดเพื่อป้องกันการใช้ฐานข้อมูลที่ปิดแล้ว กรุณาเปิดใหม่และตรวจสอบข้อมูลเดิม", Toast.LENGTH_LONG).show()
                                    delay(900)
                                    Process.killProcess(Process.myPid())
                                } else {
                                    error = e.message ?: "กู้คืนข้อมูลไม่สำเร็จ"
                                    busy = false
                                    showRestoreConfirmation = false
                                    restoreUri = null
                                }
                            }
                        }
                    }
                ) { Text("ยืนยันกู้คืน") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false; restoreUri = null }) {
                    Text("กลับ")
                }
            }
        )
    }
}
