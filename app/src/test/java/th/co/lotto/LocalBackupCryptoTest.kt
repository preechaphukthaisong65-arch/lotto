package th.co.lotto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBackupCryptoTest {
    @Test
    fun encryptedPayloadRoundTrips() {
        val original = "Lotto local backup payload".toByteArray()
        val encrypted = LocalBackupManager.encrypt(original, "correct horse battery".toCharArray())
        assertNotEquals(original.toList(), encrypted.toList())
        assertArrayEquals(original, LocalBackupManager.decrypt(encrypted, "correct horse battery".toCharArray()))
    }

    @Test
    fun streamingBackupRoundTrips() {
        val original = ByteArray(64 * 1024) { (it % 251).toByte() }
        val passphrase = "correct horse battery".toCharArray()
        val encrypted = java.io.ByteArrayOutputStream()
        LocalBackupManager.encryptStream(java.io.ByteArrayInputStream(original), encrypted, passphrase)
        val restored = java.io.ByteArrayOutputStream()
        LocalBackupManager.decryptStream(java.io.ByteArrayInputStream(encrypted.toByteArray()), restored, passphrase)
        assertArrayEquals(original, restored.toByteArray())
    }

    @Test
    fun streamingRestoreRejectsWrongPassphraseAndTampering() {
        val passphrase = "correct horse battery".toCharArray()
        val encrypted = java.io.ByteArrayOutputStream()
        LocalBackupManager.encryptStream(
            java.io.ByteArrayInputStream("backup".toByteArray()), encrypted, passphrase
        )
        val bytes = encrypted.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupManager.decryptStream(
                java.io.ByteArrayInputStream(bytes), java.io.ByteArrayOutputStream(),
                "incorrect horse battery".toCharArray()
            )
        }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupManager.decryptStream(
                java.io.ByteArrayInputStream(bytes), java.io.ByteArrayOutputStream(), passphrase
            )
        }
    }

    @Test
    fun eachBackupUsesFreshSaltAndNonce() {
        val payload = "same local backup".toByteArray()
        val passphrase = "correct horse battery".toCharArray()
        val first = LocalBackupManager.encrypt(payload, passphrase)
        val second = LocalBackupManager.encrypt(payload, passphrase)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val encrypted = LocalBackupManager.encrypt(byteArrayOf(1, 2, 3), "correct horse battery".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupManager.decrypt(encrypted, "wrong horse battery".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = LocalBackupManager.encrypt(byteArrayOf(1, 2, 3), "correct horse battery".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupManager.decrypt(encrypted, "correct horse battery".toCharArray())
        }
    }

    @Test
    fun shortPassphraseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupManager.encrypt(byteArrayOf(1), "short".toCharArray())
        }
    }
}
