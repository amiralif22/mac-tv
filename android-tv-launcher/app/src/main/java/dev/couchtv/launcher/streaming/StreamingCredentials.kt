package dev.couchtv.launcher.streaming

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where streaming sessions live: an AES-256-GCM file whose key is held by the Android Keystore.
 *
 * Nothing in DataStore is secret; tokens are here. If the keystore entry is lost (a factory reset, a
 * restored backup on another device), [EncryptedSharedPreferences] throws — we drop the file and the
 * user signs in again rather than crashing on launch.
 */
class StreamingCredentials(context: Context) {

    private val prefs: SharedPreferences? = open(context.applicationContext)

    private fun open(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // A broken keystore means "sign in again", never a crash loop.
        context.deleteSharedPreferences(FILE)
        null
    }

    fun get(serviceId: String, key: String): String? = runCatching { prefs?.getString("$serviceId.$key", null) }.getOrNull()

    fun put(serviceId: String, key: String, value: String?) {
        runCatching {
            prefs?.edit()?.apply { if (value == null) remove("$serviceId.$key") else putString("$serviceId.$key", value) }?.apply()
        }
    }

    fun clear(serviceId: String) {
        runCatching {
            val keys = prefs?.all?.keys?.filter { it.startsWith("$serviceId.") } ?: emptyList()
            prefs?.edit()?.apply { keys.forEach(::remove) }?.apply()
        }
    }

    private companion object {
        const val FILE = "secure_prefs"
    }
}
