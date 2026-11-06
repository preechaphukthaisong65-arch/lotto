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
