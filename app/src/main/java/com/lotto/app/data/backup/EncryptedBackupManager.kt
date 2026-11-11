package com.lotto.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import android.net.Uri
import androidx.room.Room
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
    private val database: LottoDatabase,
    private val databasePassphrase: ByteArray
) {
    @Volatile
    var requiresRestartForDatabaseReload: Boolean = false
        private set

    companion object {
        fun recoverInterruptedRestore(context: Context) {
            val target = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
            val parent = target.parentFile ?: return
            val previous = File(parent, "${LottoDatabase.DATABASE_NAME}.pre_restore")
            val replacement = File(parent, "${LottoDatabase.DATABASE_NAME}.restore.tmp")
            // Remove plaintext temporary snapshots left behind by a process interruption.
            listOf(
                "lotto-restore-candidate.db",
                "lotto-backup-snapshot.db",
                "lotto-backup-migration.db"
            ).forEach { File(context.cacheDir, it).delete() }
            if (!target.exists() && previous.exists()) {
                check(previous.renameTo(target)) {
                    "ไม่สามารถกู้คืนฐานข้อมูลเดิมหลังการกู้คืนที่ขัดข้องได้"
                }
            }
            if (target.exists()) previous.delete()
            replacement.delete()
        }

        private val MAGIC = "LOTTOBK1".toByteArray(StandardCharsets.US_ASCII)
        private const val ITERATIONS = 310_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MIN_PASSWORD_LENGTH = 12
        private const val MAX_BACKUP_BYTES = 256L * 1024L * 1024L
    }

    fun exportEncrypted(uri: Uri, password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "รหัสผ่านสำรองข้อมูลต้องมีอย่างน้อย 12 ตัวอักษร"
        }
        val snapshot = File(context.cacheDir, "lotto-backup-snapshot.db")
        try {
            val snapshotDirectory = snapshot.parentFile
                ?: throw IllegalStateException("ไม่พบโฟลเดอร์ชั่วคราวสำหรับสร้างข้อมูลสำรอง")
            check(snapshotDirectory.isDirectory || snapshotDirectory.mkdirs()) {
                "ไม่สามารถสร้างโฟลเดอร์ชั่วคราวสำหรับข้อมูลสำรองได้"
            }
            if (snapshot.exists()) {
                check(snapshot.delete()) {
                    "ไม่สามารถลบไฟล์ Snapshot เก่าก่อนสำรองข้อมูลได้"
                }
            }
            check(snapshot.createNewFile() && snapshot.canRead() && snapshot.canWrite()) {
                "ไม่สามารถเตรียมไฟล์ Snapshot สำหรับสำรองข้อมูลได้"
            }

            val dbFile = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
            require(dbFile.exists()) { "ไม่พบฐานข้อมูลสำหรับสำรอง" }

            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IllegalStateException("ไม่สามารถเตรียมฐานข้อมูลเพื่อสำรองได้ กรุณาลองใหม่")
                }
            }
            exportPlaintextSnapshot(dbFile, snapshot)
            require(snapshot.length() in 1..MAX_BACKUP_BYTES) {
                "ฐานข้อมูลมีขนาดเกินขีดจำกัดการสำรองข้อมูล 256 MiB"
            }
            validatePlainDatabase(snapshot)
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
                        FileInputStream(snapshot).use { input ->
                            copyWithLimit(input, encrypted)
                        }
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
                            FileOutputStream(decrypted, false).use { output ->
                                copyWithLimit(encrypted, output)
                            }
                        }
                    } catch (_: Exception) {
                        throw IllegalArgumentException("ถอดรหัสไม่สำเร็จ รหัสผ่านไม่ถูกต้องหรือไฟล์เสียหาย")
                    }
                }
            } ?: throw IllegalStateException("ไม่สามารถอ่านไฟล์สำรองได้")

            migratePlainBackupToCurrent(decrypted)
            validatePlainDatabase(decrypted)
            val target = context.getDatabasePath(LottoDatabase.DATABASE_NAME)
            val parent = target.parentFile ?: throw IllegalStateException("ไม่พบโฟลเดอร์ฐานข้อมูล")
            parent.mkdirs()
            val replacement = File(parent, "${LottoDatabase.DATABASE_NAME}.restore.tmp")
            val previous = File(parent, "${LottoDatabase.DATABASE_NAME}.pre_restore")
            encryptPlaintextDatabase(decrypted, replacement)
            check(isEncryptedDatabaseValid(replacement)) {
                "ฐานข้อมูลหลังเข้ารหัสจากไฟล์สำรองไม่ผ่านการตรวจสอบ จึงยกเลิกการกู้คืน"
            }
            FileOutputStream(replacement, true).use { it.fd.sync() }

            // Checkpoint the current database before touching it, then replace it atomically.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IllegalStateException("ไม่สามารถปิดธุรกรรมฐานข้อมูลเดิมได้ จึงยกเลิกการกู้คืน")
                }
            }
            database.close()
            requiresRestartForDatabaseReload = true
            previous.delete()
            if (target.exists() && !target.renameTo(previous)) {
                throw IllegalStateException("ไม่สามารถเก็บฐานข้อมูลเดิมไว้เป็นข้อมูลสำรองชั่วคราวได้")
            }
            try {
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val sidecar = File(target.path + suffix)
                    if (sidecar.exists() && !sidecar.delete()) {
                        throw IllegalStateException("ไม่สามารถล้างไฟล์ธุรกรรมเก่าก่อนกู้คืนได้")
                    }
                }
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

    private fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream
    ) {
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes = Math.addExact(totalBytes, read.toLong())
            require(totalBytes <= MAX_BACKUP_BYTES) {
                "ไฟล์สำรองมีขนาดเกินขีดจำกัด 256 MiB"
            }
            output.write(buffer, 0, read)
        }
        output.flush()
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

    private fun setUserVersion(file: File, passphrase: ByteArray, version: Int) {
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

    private fun exportPlaintextSnapshot(encryptedFile: File, plaintextTarget: File) {
        val source = CipherDatabase.openDatabase(
            encryptedFile.absolutePath,
            databasePassphrase,
            null,
            CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
            null
        )
        val schemaVersion = try {
            require(encryptedFile.isFile && encryptedFile.length() > 0L) {
                "ไม่พบฐานข้อมูลเข้ารหัสต้นทางสำหรับสำรอง"
            }
            val version = source.version
            source.rawExecSQL(
                "ATTACH DATABASE ? AS plaintext KEY ''",
                plaintextTarget.absolutePath
            )
            checkAttachedDatabase(source, "plaintext", plaintextTarget)
            try {
                source.rawExecSQL("SELECT sqlcipher_export('plaintext')")
            } finally {
                source.rawExecSQL("DETACH DATABASE plaintext")
            }
            version
        } finally {
            source.close()
        }
        setUserVersion(plaintextTarget, ByteArray(0), schemaVersion)
    }

    private fun encryptPlaintextDatabase(plaintextFile: File, encryptedTarget: File) {
        encryptedTarget.delete()
        val source = CipherDatabase.openDatabase(
            plaintextFile.absolutePath,
            ByteArray(0),
            null,
            CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
            null
        )
        val schemaVersion = try {
            val version = source.version
            require(plaintextFile.isFile && plaintextFile.length() > 0L) {
                "ไม่พบฐานข้อมูลต้นทางสำหรับเข้ารหัส"
            }
            source.rawExecSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                encryptedTarget.absolutePath,
                databasePassphrase
            )
            checkAttachedDatabase(source, "encrypted", encryptedTarget)
            try {
                source.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            } finally {
                source.rawExecSQL("DETACH DATABASE encrypted")
            }
            version
        } finally {
            source.close()
        }
        check(encryptedTarget.isFile && encryptedTarget.length() > 0L) {
            "การเข้ารหัส Snapshot ไม่สร้างไฟล์ฐานข้อมูลปลายทาง"
        }
        setUserVersion(encryptedTarget, databasePassphrase, schemaVersion)
    }

    private fun migratePlainBackupToCurrent(file: File) {
        val original = PlatformSQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            PlatformSQLiteDatabase.OPEN_READONLY
        )
        val sourceVersion = try {
            original.version.also {
                original.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                        "ฐานข้อมูลสำรองไม่ผ่านการตรวจสอบความสมบูรณ์"
                    }
                }
            }
        } finally {
            original.close()
        }
        require(sourceVersion in 1..LottoDatabase.DATABASE_VERSION) {
            "เวอร์ชันฐานข้อมูลสำรองใหม่กว่าแอปนี้หรือไม่รองรับ"
        }
        val migrated = File(context.cacheDir, "lotto-backup-migration.db")
        migrated.delete()
        file.copyTo(migrated, overwrite = true)
        try {
            val migrationDatabase = Room.databaseBuilder(
                context,
                LottoDatabase::class.java,
                migrated.absolutePath
            )
                .addMigrations(*LottoDatabase.ALL_MIGRATIONS)
                .build()
            try {
                migrationDatabase.openHelper.writableDatabase
            } finally {
                migrationDatabase.close()
            }
            val migratedCheck = PlatformSQLiteDatabase.openDatabase(
                migrated.absolutePath,
                null,
                PlatformSQLiteDatabase.OPEN_READONLY
            )
            try {
                require(migratedCheck.version == LottoDatabase.DATABASE_VERSION) {
                    "ไม่สามารถปรับเวอร์ชันฐานข้อมูลสำรองให้ตรงกับแอปได้"
                }
                migratedCheck.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                        "ฐานข้อมูลหลังปรับเวอร์ชันไม่ผ่านการตรวจสอบ"
                    }
                }
            } finally {
                migratedCheck.close()
            }
            migrated.copyTo(file, overwrite = true)
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                File(migrated.absolutePath + suffix).delete()
            }
        }
    }

    private fun validatePlainDatabase(file: File) {
        val candidate = PlatformSQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            PlatformSQLiteDatabase.OPEN_READONLY
        )
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

    private fun isEncryptedDatabaseValid(file: File): Boolean = try {
        val candidate = CipherDatabase.openDatabase(
            file.absolutePath,
            databasePassphrase,
            null,
            CipherDatabase.OPEN_READONLY,
            null
        )
        try {
            candidate.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok" &&
                    candidate.version == LottoDatabase.DATABASE_VERSION
            }
        } finally {
            candidate.close()
        }
    } catch (_: Exception) {
        false
    }
}
