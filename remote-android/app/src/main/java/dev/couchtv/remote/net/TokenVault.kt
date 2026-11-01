package dev.couchtv.remote.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pairing tokens, encrypted with a key that never leaves the Android Keystore.
 *
 * A token is a capability — whoever holds it can drive the TV — so it is not stored in plain text,
 * not backed up, and not logged. A device that loses its key simply has to pair again, which is the
 * correct failure: the TV's own device list can revoke it too.
 */
class TokenVault(context: Context) {

    private val preferences = context.getSharedPreferences("couchremote.tokens", Context.MODE_PRIVATE)

    fun token(deviceId: String): String? {
        val stored = preferences.getString(keyFor(deviceId), null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun save(deviceId: String, token: String) {
        runCatching { preferences.edit().putString(keyFor(deviceId), encrypt(token)).apply() }
    }

    fun clear(deviceId: String) {
        preferences.edit().remove(keyFor(deviceId)).apply()
    }

    private fun keyFor(deviceId: String) = "token.$deviceId"

    // ---- Crypto -------------------------------------------------------------------------------

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // The IV must be stored with the ciphertext; it is not secret.
        val packed = cipher.iv + body
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, IV_BYTES)
        val body = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "couchremote.token.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
