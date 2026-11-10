package com.lotto.app.security

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lotto.app.LottoApplication
import com.lotto.app.core.di.AppContainer
import com.lotto.app.data.local.LottoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionInstrumentedTest {
    @Test
    fun liveDatabaseIsEncryptedAndPasswordBackupRestoresWithoutDataLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as LottoApplication
        val testKey = "instrumented_backup_roundtrip"
        val secret = "LongBackupPassword-2026"
        application.container.settingsRepository.save(testKey, "must-survive-restore")

        val databaseFile = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
        assertTrue("Room database file should exist after a write", databaseFile.exists())
        val header = ByteArray(16)
        FileInputStream(databaseFile).use { input ->
            assertTrue("Database file should contain a complete header", input.read(header) == header.size)
        }
        assertFalse(
            "SQLCipher database must not expose the plaintext SQLite header",
            String(header, StandardCharsets.US_ASCII).startsWith("SQLite format 3")
        )

        val backup = File(context.cacheDir, "lotto-backup-roundtrip.lbackup")
        val uri = Uri.fromFile(backup)
        application.container.encryptedBackupManager.exportEncrypted(uri, secret.toCharArray())
        assertTrue("Encrypted backup file should exist", backup.exists() && backup.length() > 0L)

        assertThrows(IllegalArgumentException::class.java) {
            application.container.encryptedBackupManager.restoreEncrypted(
                uri,
                "WrongBackupPassword-2026".toCharArray()
            )
        }
        assertEquals("must-survive-restore", application.container.settingsRepository.get(testKey))

        application.container.settingsRepository.delete(testKey)
        application.container.encryptedBackupManager.restoreEncrypted(uri, secret.toCharArray())

        val restoredContainer = AppContainer(context)
        assertEquals("must-survive-restore", restoredContainer.settingsRepository.get(testKey))
        restoredContainer.settingsRepository.delete(testKey)
        backup.delete()
    }
}
