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
 * Packs the AES-GCM initialisation vector in front of its ciphertext.
 *
 * Kept apart from the Keystore code so it can be tested on a plain JVM: the Keystore itself exists
 * only on a device, but the framing is ordinary byte arithmetic and a mistake in it would silently
 * make every stored token undecryptable.
 */
internal object TokenFraming {

    /** Initialisation vector length for AES-GCM, in bytes. */
    const val IV_BYTES = 12

    /** `iv || body` — the vector is not secret, so it travels with the ciphertext. */
    fun pack(iv: ByteArray, body: ByteArray): ByteArray = iv + body

    /**
     * Returns the vector and the ciphertext, or null when the blob is shorter than the vector.
     * An empty body is legal, so `split(pack(iv, ByteArray(0)))` round trips.
     */
    fun split(packed: ByteArray): Pair<ByteArray, ByteArray>? {
        if (packed.size < IV_BYTES) return null
        return packed.copyOfRange(0, IV_BYTES) to packed.copyOfRange(IV_BYTES, packed.size)
    }
}

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
        val packed = TokenFraming.pack(cipher.iv, body)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        // A truncated or foreign blob means the key changed: fail here rather than in the cipher.
        val (iv, body) = TokenFraming.split(packed) ?: error("stored token is truncated")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "couchremote.token.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
