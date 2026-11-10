package com.lotto.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.lotto.app.data.local.LottoDatabase
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedBackupManager(
    private val context: Context,
    private val database: LottoDatabase
) {
    companion object {
        private val MAGIC = "LOTTOBK1".toByteArray(StandardCharsets.US_ASCII)
        private const val ITERATIONS = 310_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MIN_PASSWORD_LENGTH = 12
    }

    fun exportEncrypted(uri: Uri, password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "รหัสผ่านสำรองข้อมูลต้องมีอย่างน้อย 12 ตัวอักษร"
        }
        val snapshot = File(context.cacheDir, "lotto-backup-snapshot.db")
        try {
            val dbFile = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
            require(dbFile.exists()) { "ไม่พบฐานข้อมูลสำหรับสำรอง" }

            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IllegalStateException("ไม่สามารถเตรียมฐานข้อมูลเพื่อสำรองได้ กรุณาลองใหม่")
                }
            }
            database.runInTransaction {
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(snapshot, false).use { output -> input.copyTo(output) }
                }
            }
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
            val cipher = createCipher(Cipher.ENCRYPT_MODE, password, salt, iv)

            context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
                DataOutputStream(raw).use { header ->
                    header.write(MAGIC)
                    header.writeInt(ITERATIONS)
                    header.write(salt)
                    header.write(iv)
                    header.flush()
                    CipherOutputStream(header, cipher).use { encrypted ->
                        FileInputStream(snapshot).use { input -> input.copyTo(encrypted) }
                    }
                }
            } ?: throw IllegalStateException("ไม่สามารถสร้างไฟล์สำรองได้")
        } finally {
            password.fill('\u0000')
            snapshot.delete()
        }
    }

    fun restoreEncrypted(uri: Uri, password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "รหัสผ่านสำรองข้อมูลไม่ถูกต้อง"
        }
        val decrypted = File(context.cacheDir, "lotto-restore-candidate.db")
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                DataInputStream(raw).use { input ->
                    val magic = ByteArray(MAGIC.size)
                    input.readFully(magic)
                    require(magic.contentEquals(MAGIC)) { "ไฟล์นี้ไม่ใช่ข้อมูลสำรอง Lotto ที่รองรับ" }
                    val iterations = input.readInt()
                    require(iterations in 200_000..1_000_000) { "รูปแบบการเข้ารหัสในไฟล์ไม่ถูกต้อง" }
                    val salt = ByteArray(SALT_BYTES).also { input.readFully(it) }
                    val iv = ByteArray(IV_BYTES).also { input.readFully(it) }
                    val cipher = createCipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations)
                    try {
                        CipherInputStream(input, cipher).use { encrypted ->
                            FileOutputStream(decrypted, false).use { output -> encrypted.copyTo(output) }
                        }
                    } catch (_: Exception) {
                        throw IllegalArgumentException("ถอดรหัสไม่สำเร็จ รหัสผ่านไม่ถูกต้องหรือไฟล์เสียหาย")
                    }
                }
            } ?: throw IllegalStateException("ไม่สามารถอ่านไฟล์สำรองได้")

            validateDatabase(decrypted)
            val target = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
            val parent = target.parentFile ?: throw IllegalStateException("ไม่พบโฟลเดอร์ฐานข้อมูล")
            parent.mkdirs()
            val replacement = File(parent, "${LottoDatabase.DATABASE_NAME}.restore.tmp")
            val previous = File(parent, "${LottoDatabase.DATABASE_NAME}.pre_restore")
            decrypted.copyTo(replacement, overwrite = true)
            FileOutputStream(replacement, true).use { it.fd.sync() }

            // Checkpoint the current database before touching it, then replace it atomically.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IllegalStateException("ไม่สามารถปิดธุรกรรมฐานข้อมูลเดิมได้ จึงยกเลิกการกู้คืน")
                }
            }
            database.close()
            previous.delete()
            if (target.exists() && !target.renameTo(previous)) {
                throw IllegalStateException("ไม่สามารถเก็บฐานข้อมูลเดิมไว้เป็นข้อมูลสำรองชั่วคราวได้")
            }
            try {
                File(target.path + "-wal").delete()
                File(target.path + "-shm").delete()
                File(target.path + "-journal").delete()
                if (!replacement.renameTo(target)) {
                    throw IllegalStateException("ไม่สามารถแทนที่ฐานข้อมูลด้วยไฟล์กู้คืนได้")
                }
                previous.delete()
            } catch (failure: Exception) {
                if (!target.exists() && previous.exists()) previous.renameTo(target)
                throw failure
            }
        } finally {
            password.fill('\u0000')
            decrypted.delete()
        }
    }

    private fun createCipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int = ITERATIONS
    ): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            keyBytes.fill(0)
        }
    }

    private fun validateDatabase(file: File) {
        val candidate = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            candidate.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "ฐานข้อมูลในไฟล์สำรองไม่ผ่านการตรวจสอบความสมบูรณ์"
                }
            }
            require(candidate.version == LottoDatabase.DATABASE_VERSION) {
                "เวอร์ชันฐานข้อมูลในไฟล์สำรองไม่ตรงกับแอปนี้ กรุณาใช้แอปเวอร์ชันที่ตรงกัน"
            }
        } finally {
            candidate.close()
        }
    }
}
