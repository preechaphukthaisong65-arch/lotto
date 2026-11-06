package th.co.lotto

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import th.co.lotto.data.LottoDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted, device-local backup. The passphrase-derived AES-GCM key
 * is deliberately portable between devices; no cloud service or device-bound key
 * is required. The archive contains the Room database and locally stored receipts.
 */
object LocalBackupManager {
    private val magic = "LOTTOBK1".toByteArray(Charsets.US_ASCII)
    private const val iterations = 210_000
    private const val saltSize = 16
    private const val nonceSize = 12
    private const val maxBackupBytes = 256 * 1024 * 1024
    private const val currentSchemaVersion = 8

    fun writeEncryptedBackup(
        context: Context,
        database: LottoDatabase,
        passphrase: CharArray,
        destination: java.io.OutputStream
    ) {
        validatePassphrase(passphrase)
        val checkpoint = database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)")
        try {
            require(checkpoint.moveToFirst() && checkpoint.getInt(0) == 0) {
                "ฐานข้อมูลกำลังถูกใช้งาน ไม่สามารถสำรองข้อมูลอย่างปลอดภัยได้"
            }
        } finally {
            checkpoint.close()
        }
        val archiveFile = File(context.cacheDir, "lotto-backup-${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
                val databaseFile = context.getDatabasePath("lotto-local.db")
                require(databaseFile.isFile && databaseFile.length() > 0L) { "ไม่พบฐานข้อมูลในเครื่อง" }
                zip.putNextEntry(ZipEntry("lotto-local.db"))
                FileInputStream(databaseFile).use { it.copyTo(zip) }
                zip.closeEntry()

                val receipts = File(context.filesDir, "receipts")
                if (receipts.exists()) {
                    require(receipts.isDirectory) { "โฟลเดอร์ใบเสร็จไม่ถูกต้อง" }
                    receipts.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(receipts).invariantSeparatorsPath
                        require(!relative.startsWith("/") && !relative.split('/').any { it == ".." }) {
                            "พบเส้นทางไฟล์ใบเสร็จที่ไม่ปลอดภัย"
                        }
                        zip.putNextEntry(ZipEntry("receipts/$relative"))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("backup-info.txt"))
                zip.write("LOTTO LOCAL BACKUP\nschema=$currentSchemaVersion\ncreatedAt=${System.currentTimeMillis()}\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            require(archiveFile.length() in 1..maxBackupBytes.toLong()) { "ข้อมูลสำรองมีขนาดเกิน 256 MB" }

            FileInputStream(archiveFile).use { input ->
                encryptStream(input, destination, passphrase)
            }
        } finally {
            archiveFile.delete()
        }
    }

    fun restoreEncryptedBackup(context: Context, encryptedInput: java.io.InputStream, passphrase: CharArray) {
        validatePassphrase(passphrase)
        val staging = File(context.cacheDir, "lotto-restore-${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        val archiveFile = File(staging, "backup.zip")
        val extractedDb = File(staging, "lotto-local.db")
        val extractedReceipts = File(staging, "receipts")
        try {
            FileOutputStream(archiveFile).use { output ->
                decryptStream(encryptedInput, output, passphrase)
            }
            require(archiveFile.isFile && archiveFile.length() > 0L) { "ไฟล์สำรองไม่มีข้อมูล" }

            var totalExtracted = 0L
            var entryCount = 0
            val seenEntries = mutableSetOf<String>()
            ZipInputStream(FileInputStream(archiveFile)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= 10_000) { "ไฟล์สำรองมีจำนวนรายการมากเกินไป" }
                    val name = entry.name
                    require(seenEntries.add(name)) { "ไฟล์สำรองมีรายการซ้ำ" }
                    require(!name.startsWith("/") && !name.contains('\\') &&
                        name.split('/').none { it == ".." || it == "." || it.isEmpty() }) {
                        "ไฟล์สำรองมีเส้นทางที่ไม่ปลอดภัย"
                    }
                    val destination = when {
                        name == "lotto-local.db" -> extractedDb
                        name == "backup-info.txt" -> File(staging, "backup-info.txt")
                        name == "receipts" -> extractedReceipts
                        name.startsWith("receipts/") -> {
                            val relative = name.removePrefix("receipts/")
                            require(relative.isNotBlank())
                            File(extractedReceipts, relative)
                        }
                        else -> error("ไฟล์สำรองมีข้อมูลที่ไม่รู้จัก")
                    }
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalExtracted = Math.addExact(totalExtracted, count.toLong())
                                require(totalExtracted <= maxBackupBytes) { "ข้อมูลที่แตกไฟล์มีขนาดเกินกำหนด" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(extractedDb.isFile && extractedDb.length() > 0L) { "ไฟล์สำรองไม่มีฐานข้อมูล Lotto" }
            validateDatabase(extractedDb)

            val targetDb = context.getDatabasePath("lotto-local.db")
            targetDb.parentFile?.mkdirs()
            val oldDb = File(staging, "previous.db")
            val targetReceipts = File(context.filesDir, "receipts")
            val oldReceipts = File(staging, "previous-receipts")
            val newReceipts = File(staging, "new-receipts")
            if (extractedReceipts.exists()) {
                require(extractedReceipts.renameTo(newReceipts)) { "เตรียมโฟลเดอร์ใบเสร็จไม่สำเร็จ" }
            } else {
                newReceipts.mkdirs()
            }

            LottoDatabase.closeForRestore()
            var oldDbMoved = false
            var oldReceiptsMoved = false
            var newDbInstalled = false
            var newReceiptsInstalled = false
            try {
                if (targetDb.exists()) {
                    require(targetDb.renameTo(oldDb)) { "ไม่สามารถสำรองฐานข้อมูลเดิมเพื่อกู้คืนได้" }
                    oldDbMoved = true
                }
                File(context.getDatabasePath("lotto-local.db-wal").path).delete()
                File(context.getDatabasePath("lotto-local.db-shm").path).delete()
                File(context.getDatabasePath("lotto-local.db-journal").path).delete()
                require(extractedDb.renameTo(targetDb)) { "ติดตั้งฐานข้อมูลที่กู้คืนไม่สำเร็จ" }
                newDbInstalled = true

                if (targetReceipts.exists()) {
                    require(targetReceipts.renameTo(oldReceipts)) { "ไม่สามารถสำรองใบเสร็จเดิมได้" }
                    oldReceiptsMoved = true
                }
                require(newReceipts.renameTo(targetReceipts)) { "ติดตั้งใบเสร็จที่กู้คืนไม่สำเร็จ" }
                newReceiptsInstalled = true

                val reopened = LottoDatabase.getInstance(context)
                reopened.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                    require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                        "ฐานข้อมูลหลังการกู้คืนไม่ผ่าน quick_check"
                    }
                }
                reopened.lottoDao().insertAuditEvent(
                    th.co.lotto.data.AuditEventEntity(
                        java.util.UUID.randomUUID().toString(),
                        "LOCAL_BACKUP",
                        "DATABASE",
                        "RESTORED",
                        "Encrypted local backup restored",
                        System.currentTimeMillis()
                    )
                )
            } catch (failure: Exception) {
                runCatching { LottoDatabase.closeForRestore() }
                if (newReceiptsInstalled) targetReceipts.deleteRecursively()
                if (oldReceiptsMoved) oldReceipts.renameTo(targetReceipts)
                if (newDbInstalled) targetDb.delete()
                if (oldDbMoved) oldDb.renameTo(targetDb)
                throw failure
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    internal fun encryptStream(input: java.io.InputStream, destination: java.io.OutputStream, passphrase: CharArray) {
        validatePassphrase(passphrase)
        val salt = ByteArray(saltSize).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(nonceSize).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
        val header = DataOutputStream(destination)
        header.write(magic)
        header.writeInt(iterations)
        header.write(salt)
        header.write(nonce)
        header.flush()
        javax.crypto.CipherOutputStream(destination, cipher).use { encryptedOutput ->
            input.copyTo(encryptedOutput)
        }
    }

    internal fun decryptStream(
        encryptedInput: java.io.InputStream,
        destination: java.io.OutputStream,
        passphrase: CharArray
    ) {
        validatePassphrase(passphrase)
        val input = DataInputStream(encryptedInput)
        val header = ByteArray(magic.size)
        input.readFully(header)
        require(header.contentEquals(magic)) { "รูปแบบไฟล์สำรองไม่ถูกต้อง" }
        val rounds = input.readInt()
        require(rounds in 100_000..1_000_000) { "ค่าความปลอดภัยของไฟล์สำรองไม่ถูกต้อง" }
        val salt = ByteArray(saltSize).also(input::readFully)
        val nonce = ByteArray(nonceSize).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, rounds), GCMParameterSpec(128, nonce))
        var encryptedCount = magic.size + 4 + saltSize + nonceSize
        var plainCount = 0L
        try {
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                encryptedCount = Math.addExact(encryptedCount, count)
                require(encryptedCount <= maxBackupBytes + 64) { "ไฟล์สำรองมีขนาดเกิน 256 MB" }
                val decoded = cipher.update(buffer, 0, count)
                if (decoded != null && decoded.isNotEmpty()) {
                    plainCount = Math.addExact(plainCount, decoded.size.toLong())
                    require(plainCount <= maxBackupBytes) { "ข้อมูลสำรองมีขนาดเกินกำหนด" }
                    destination.write(decoded)
                }
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                plainCount = Math.addExact(plainCount, finalBytes.size.toLong())
                require(plainCount <= maxBackupBytes) { "ข้อมูลสำรองมีขนาดเกินกำหนด" }
                destination.write(finalBytes)
            }
            destination.flush()
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("รหัสผ่านไม่ถูกต้องหรือไฟล์สำรองถูกแก้ไข")
        }
    }

    internal fun encrypt(plainText: ByteArray, passphrase: CharArray): ByteArray {
        validatePassphrase(passphrase)
        val salt = ByteArray(saltSize).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(nonceSize).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plainText)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(magic)
                output.writeInt(iterations)
                output.write(salt)
                output.write(nonce)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    internal fun decrypt(encryptedBytes: ByteArray, passphrase: CharArray): ByteArray {
        validatePassphrase(passphrase)
        DataInputStream(ByteArrayInputStream(encryptedBytes)).use { input ->
            val header = ByteArray(magic.size)
            input.readFully(header)
            require(header.contentEquals(magic)) { "รูปแบบไฟล์สำรองไม่ถูกต้อง" }
            val rounds = input.readInt()
            require(rounds in 100_000..1_000_000) { "ค่าความปลอดภัยของไฟล์สำรองไม่ถูกต้อง" }
            val salt = ByteArray(saltSize).also(input::readFully)
            val nonce = ByteArray(nonceSize).also(input::readFully)
            val ciphertext = input.readBytes()
            require(ciphertext.size >= 16) { "ไฟล์สำรองเสียหาย" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, rounds), GCMParameterSpec(128, nonce))
            return try {
                cipher.doFinal(ciphertext)
            } catch (_: AEADBadTagException) {
                throw IllegalArgumentException("รหัสผ่านไม่ถูกต้องหรือไฟล์สำรองถูกแก้ไข")
            }
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, rounds: Int = iterations): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, rounds, 256)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun validatePassphrase(passphrase: CharArray) {
        require(passphrase.size >= 10) { "รหัสผ่านสำรองต้องมีอย่างน้อย 10 ตัวอักษร" }
    }

    private fun validateDatabase(file: File) {
        val sqlite = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "ฐานข้อมูลในไฟล์สำรองไม่ผ่าน integrity_check"
                }
            }
            sqlite.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                require(!cursor.moveToFirst()) { "ฐานข้อมูลในไฟล์สำรองมี foreign key ผิดพลาด" }
            }
            sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(cursor.moveToFirst()) { "ไม่พบเวอร์ชันฐานข้อมูล" }
                val version = cursor.getInt(0)
                require(version in 1..currentSchemaVersion) { "เวอร์ชันฐานข้อมูลไม่รองรับ: $version" }
            }
            val requiredTables = setOf("draw_periods", "sale_documents", "sale_lines", "audit_events")
            val foundTables = mutableSetOf<String>()
            sqlite.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { cursor -> while (cursor.moveToNext()) foundTables.add(cursor.getString(0)) }
            require(foundTables.containsAll(requiredTables)) { "ฐานข้อมูลในไฟล์สำรองไม่ใช่ฐานข้อมูล Lotto ที่สมบูรณ์" }
        } finally {
            sqlite.close()
        }
    }
}
