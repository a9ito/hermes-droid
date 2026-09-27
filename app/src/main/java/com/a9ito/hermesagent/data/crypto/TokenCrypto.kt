package com.a9ito.hermesagent.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts the API token with a hardware-backed key held in the
 * AndroidKeyStore. The key material never leaves the Keystore; we only ever hold
 * the plaintext token transiently in memory to build the Authorization header.
 *
 * Storage format (Base64, no wrap): [12-byte GCM IV][ciphertext+tag]. Persisted
 * in DataStore by [com.a9ito.hermesagent.data.SettingsRepository]; a backup copy
 * would be useless without the non-exportable Keystore key (and the datastore
 * dir is excluded from backup anyway).
 *
 * The token is NEVER logged. No function here emits the plaintext to Logcat.
 */
class TokenCrypto {

    /** Encrypt [plaintext]; returns Base64(iv|ciphertext). */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt a value produced by [encrypt]. Returns null on any failure
     * (tampered data, key invalidated by a lock-screen change, malformed input)
     * so callers degrade to "no token configured" rather than crashing.
     */
    fun decrypt(stored: String): String? {
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Not requiring auth: the app must read the token headlessly to make
            // requests. Confidentiality-at-rest comes from the non-exportable key.
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hermes_agent_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
