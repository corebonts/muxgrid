package io.github.corebonts.muxgrid.core.data

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CredentialCipherTest {

    private fun newTestAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Test
    fun `recovers from corrupted keyset by clearing and regenerating aead`() {
        val buildAttempts = AtomicInteger(0)
        val cleared = AtomicBoolean(false)
        val oldAead = newTestAead()
        val oldCipher = CredentialCipher(
            associatedData = "ad".toByteArray(StandardCharsets.UTF_8),
            buildAead = { oldAead },
            clearCorruptedKeyset = {},
        )
        val oldEncrypted = oldCipher.encrypt("legacy-pass")

        val recoveredAead = newTestAead()
        val cipher = CredentialCipher(
            associatedData = "ad".toByteArray(StandardCharsets.UTF_8),
            buildAead = {
                if (buildAttempts.getAndIncrement() == 0) {
                    throw GeneralSecurityException("corrupted keyset")
                }
                recoveredAead
            },
            clearCorruptedKeyset = { cleared.set(true) },
        )

        val encrypted = cipher.encrypt("new-secret")
        assertTrue(cleared.get())
        assertEquals(2, buildAttempts.get())
        assertEquals("new-secret", cipher.decrypt(encrypted))
        // Ciphertext from wiped keyset degrades to empty string instead of throwing
        assertEquals("", cipher.decrypt(oldEncrypted))
    }

    @Test
    fun `encrypt throws instead of returning empty and does not permanently cache failure`() {
        val shouldFail = AtomicBoolean(true)
        val clearedCount = AtomicInteger(0)
        val workingAead = newTestAead()
        val cipher = CredentialCipher(
            associatedData = "ad".toByteArray(StandardCharsets.UTF_8),
            buildAead = {
                if (shouldFail.get()) {
                    throw GeneralSecurityException("keystore locked")
                }
                workingAead
            },
            clearCorruptedKeyset = { clearedCount.incrementAndGet() },
        )

        assertThrows(GeneralSecurityException::class.java) {
            cipher.encrypt("my-password")
        }
        assertEquals(1, clearedCount.get())

        // Once Keystore recovers, subsequent calls succeed without creating a new instance
        shouldFail.set(false)
        val encrypted = cipher.encrypt("my-password")
        assertEquals("my-password", cipher.decrypt(encrypted))
    }
}
