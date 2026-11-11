package com.lotto.app.data.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private fun checkAttachedDatabase(
    database: CipherDatabase,
    schema: String,
    expectedFile: File
) {
    var attachedPath: String? = null
    database.rawQuery("PRAGMA database_list", null).use { cursor ->
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == schema) {
                attachedPath = cursor.getString(2)
                break
            }
        }
    }
    val actualPath = attachedPath
        ?: throw IllegalStateException("ไม่สามารถแนบฐานข้อมูลปลายทาง ($schema) ได้")
    check(File(actualPath).canonicalFile == expectedFile.canonicalFile) {
        "ตำแหน่งฐานข้อมูลที่แนบไม่ตรงกับไฟล์ปลายทางที่กำหนด ($schema)"
    }
}

internal fun encryptLegacyDatabaseFile(
    sourceFile: File,
    encryptedTarget: File,
    passphrase: ByteArray
) {
    require(sourceFile.isFile && sourceFile.length() > 0L) {
        "ไม่พบฐานข้อมูลเดิมหรือไฟล์ฐานข้อมูลว่างเปล่า จึงยกเลิกการเข้ารหัส"
    }
    encryptedTarget.parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) { "ไม่สามารถสร้างโฟลเดอร์ฐานข้อมูลปลายทางได้" }
    }
    if (encryptedTarget.exists()) {
        check(encryptedTarget.delete()) { "ไม่สามารถลบไฟล์เข้ารหัสชั่วคราวเดิมได้" }
    }
    val legacy = CipherDatabase.openDatabase(
        sourceFile.absolutePath,
        ByteArray(0),
        null,
        CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
        null
    )
    val schemaVersion = legacy.version
    try {
        legacy.rawExecSQL(
            "ATTACH DATABASE ? AS encrypted KEY ?",
            encryptedTarget.absolutePath,
            passphrase
        )
        checkAttachedDatabase(legacy, "encrypted", encryptedTarget)
        try {
            legacy.rawExecSQL("SELECT sqlcipher_export('encrypted')")
        } finally {
            legacy.rawExecSQL("DETACH DATABASE encrypted")
        }
    } finally {
        legacy.close()
    }
    check(encryptedTarget.isFile && encryptedTarget.length() > 0L) {
        "การส่งออกฐานข้อมูลเข้ารหัสไม่สร้างไฟล์ปลายทาง"
    }
    setEncryptedDatabaseVersion(encryptedTarget, passphrase, schemaVersion)
}

private fun setEncryptedDatabaseVersion(file: File, passphrase: ByteArray, version: Int) {
    val db = CipherDatabase.openDatabase(
        file.absolutePath,
        passphrase,
        null,
        CipherDatabase.OPEN_READWRITE,
        null
    )
    try {
        db.version = version
    } finally {
        db.close()
    }
}

/**
 * Keeps a random SQLCipher passphrase wrapped by an Android Keystore AES key.
 * Existing plaintext databases are converted in place only after a verified encrypted
 * copy has been produced. The plaintext original is retained until the replacement
 * has passed an integrity check.
 */
class DatabaseKeyManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        System.loadLibrary("sqlcipher")
        val encoded = preferences.getString(WRAPPED_KEY, null)
        if (encoded != null) return unwrap(Base64.decode(encoded, Base64.NO_WRAP))

        val raw = ByteArray(32).also(SecureRandom()::nextBytes)
        val wrapped = wrap(raw)
        check(preferences.edit().putString(WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()) {
            "ไม่สามารถบันทึกกุญแจเข้ารหัสฐานข้อมูลลงในพื้นที่ปลอดภัยได้"
        }
        return raw
    }

    fun ensureEncryptedDatabase(): ByteArray {
        val passphrase = getOrCreatePassphrase()
        val database = appContext.getDatabasePath(DATABASE_NAME)
        val parent = database.parentFile ?: error("ไม่พบโฟลเดอร์ฐานข้อมูล")
        val encryptedTemp = File(parent, "$DATABASE_NAME.encrypting")
        val plaintextBackup = File(parent, "$DATABASE_NAME.plaintext_migration_backup")

        // Recover an interrupted conversion before allowing Room to create/open anything.
        if (!database.exists() && plaintextBackup.exists()) {
            if (encryptedTemp.exists() && isEncryptedAndValid(encryptedTemp, passphrase)) {
                check(encryptedTemp.renameTo(database)) { "ไม่สามารถกู้คืนฐานข้อมูลที่เข้ารหัสระหว่างการเริ่มระบบได้" }
                plaintextBackup.delete()
            } else {
                check(plaintextBackup.renameTo(database)) { "ไม่สามารถคืนฐานข้อมูลเดิมหลังการเข้ารหัสที่ขัดข้องได้" }
            }
        }
        if (database.exists() && isEncryptedAndValid(database, passphrase)) {
            encryptedTemp.delete()
            plaintextBackup.delete()
            return passphrase
        }
        if (database.exists() && !isPlainDatabaseValid(database) && plaintextBackup.exists() &&
            isPlainDatabaseValid(plaintextBackup)
        ) {
            database.delete()
            check(plaintextBackup.renameTo(database)) { "ไม่สามารถคืนฐานข้อมูลเดิมที่ยังอ่านได้" }
        }
        if (!database.exists() || database.length() == 0L) return passphrase

        requirePlainDatabaseIsValid(database) // Never overwrite an unknown or corrupt database.
        encryptedTemp.delete()
        plaintextBackup.delete()
        encryptLegacyDatabaseFile(database, encryptedTemp, passphrase)

        check(encryptedTemp.exists() && isEncryptedAndValid(encryptedTemp, passphrase)) {
            "ไม่สามารถตรวจสอบฐานข้อมูลที่เข้ารหัสใหม่ได้ จึงเก็บฐานข้อมูลเดิมไว้และยกเลิกการเปลี่ยน"
        }
        // The exported encrypted snapshot is complete; remove only the legacy
        // database's now-checkpointed WAL sidecars before reusing the same path.
        deleteSqliteSidecars(database)

        check(database.renameTo(plaintextBackup)) {
            "ไม่สามารถเก็บฐานข้อมูลเดิมไว้ก่อนเข้ารหัสได้"
        }
        try {
            check(encryptedTemp.renameTo(database)) {
                "ไม่สามารถแทนที่ฐานข้อมูลเดิมด้วยฐานข้อมูลเข้ารหัสได้"
            }
            check(isEncryptedAndValid(database, passphrase)) {
                "ฐานข้อมูลที่เข้ารหัสหลังแทนที่ไม่ผ่านการตรวจสอบ"
            }
            deleteSqliteSidecars(database)
            plaintextBackup.delete()
        } catch (failure: Exception) {
            database.delete()
            check(plaintextBackup.renameTo(database)) {
                "เกิดข้อผิดพลาดระหว่างเข้ารหัส และไม่สามารถคืนฐานข้อมูลเดิมได้ กรุณาอย่าลบไฟล์ในโฟลเดอร์ข้อมูลแอป"
            }
            throw failure
        } finally {
            encryptedTemp.delete()
        }
        return passphrase
    }

    private fun isPlainDatabaseValid(file: File): Boolean = try {
        val db = PlatformSQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            PlatformSQLiteDatabase.OPEN_READONLY
        )
        try {
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
        } finally {
            db.close()
        }
    } catch (_: Exception) {
        false
    }

    private fun requirePlainDatabaseIsValid(file: File) {
        check(isPlainDatabaseValid(file)) {
            "ไม่สามารถเปิดฐานข้อมูลเดิมหรือฐานข้อมูลเข้ารหัสได้ ระบบจะไม่ลบหรือเขียนทับข้อมูลเดิม"
        }
    }

    private fun isEncryptedAndValid(file: File, passphrase: ByteArray): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val db = CipherDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                CipherDatabase.OPEN_READONLY,
                null
            )
            try {
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0) == "ok"
                }
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteSqliteSidecars(file: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(file.absolutePath + suffix)
            if (sidecar.exists()) check(sidecar.delete()) { "ไม่สามารถลบไฟล์ธุรกรรมฐานข้อมูลเดิมได้" }
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(raw)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        require(blob.isNotEmpty()) { "กุญแจเข้ารหัสฐานข้อมูลเสียหาย" }
        val ivLength = blob[0].toInt() and 0xff
        require(ivLength in 12..16 && blob.size > 1 + ivLength) { "กุญแจเข้ารหัสฐานข้อมูลเสียหาย" }
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val encrypted = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val PREFERENCES = "lotto_database_security"
        private const val WRAPPED_KEY = "wrapped_sqlcipher_key"
        private const val KEY_ALIAS = "lotto_database_wrapping_key_v1"
        private const val DATABASE_NAME = "lotto_database"

        @Volatile private var instance: DatabaseKeyManager? = null

        fun get(context: Context): DatabaseKeyManager =
            instance ?: synchronized(this) {
                instance ?: DatabaseKeyManager(context).also { instance = it }
            }
    }
}
