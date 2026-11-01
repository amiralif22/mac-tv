package dev.couchtv.remote.net

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Who this phone is, for the TV's device list.
 *
 * A random identifier generated once, plus the model name a person recognises. It is not the
 * hardware serial, not the advertising ID, and it never leaves the LAN.
 */
object DeviceIdentity {

    private const val PREFS = "couchremote.identity"
    private const val KEY_ID = "deviceId"

    fun id(context: Context): String {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences.getString(KEY_ID, null)?.let { return it }
        val fresh = UUID.randomUUID().toString().replace("-", "")
        preferences.edit().putString(KEY_ID, fresh).apply()
        return fresh
    }

    /** "Pixel 8" — enough for a person to recognise their own phone in the TV's device list. */
    fun name(context: Context): String {
        val candidate = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        return candidate?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .replaceFirstChar { it.uppercase() }
    }
}
