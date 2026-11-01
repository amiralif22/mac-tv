package dev.couchtv.launcher.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers which phones may control this TV.
 *
 * Only digests are stored (SharedPreferences, private to the app); the token itself is returned once,
 * to the phone, and rotated on every successful handshake. A device can be revoked from Settings,
 * which closes its socket.
 */
class PairingStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("paired_devices", Context.MODE_PRIVATE)
    private val lock = Any()

    data class Device(val id: String, val name: String, val digest: String, val createdAt: Long) {
        val displayName: String get() = name.ifBlank { "Phone" }
    }

    private val attempts = Pairing.Attempts()

    private var code: Pairing.Code = Pairing.Code("", 0L, 0L)

    /** The code currently on screen; a new one every five minutes. */
    fun currentCode(now: Long = System.currentTimeMillis()): Pairing.Code = synchronized(lock) {
        if (code.value.isEmpty() || code.isExpired(now)) {
            code = Pairing.newCode(now, previous = code.value.takeIf { it.isNotEmpty() })
        }
        code
    }

    fun forceNewCode(now: Long = System.currentTimeMillis()): Pairing.Code = synchronized(lock) {
        code = Pairing.newCode(now, previous = code.value.takeIf { it.isNotEmpty() })
        code
    }

    fun isBlocked(now: Long = System.currentTimeMillis()): Boolean = attempts.isBlocked(now)

    fun remainingAttempts(now: Long = System.currentTimeMillis()): Int = attempts.remaining()

    /**
     * Checks a code from a phone. Returns the device token (once) on success, null on failure.
     * A correct code creates the device; the caller decides what to tell the client.
     */
    fun pairWithPin(
        pin: String,
        deviceId: String,
        name: String,
        now: Long = System.currentTimeMillis(),
    ): String? = synchronized(lock) {
        if (attempts.isBlocked(now)) return null
        val active = currentCode(now)
        if (!Pairing.isWellFormedPin(pin) || !Pairing.constantTimeEquals(active.value, pin)) {
            attempts.record(now)
            return null
        }
        attempts.clear()
        val token = Pairing.newToken()
        val device = Device(
            id = deviceId.ifBlank { java.util.UUID.randomUUID().toString() },
            name = name.take(48),
            digest = Pairing.digest(token),
            createdAt = now,
        )
        save(devices().filterNot { it.id == device.id } + device)
        token
    }

    fun deviceForToken(token: String): Device? = synchronized(lock) {
        val digest = Pairing.digest(token)
        devices().firstOrNull { Pairing.constantTimeEquals(it.digest, digest) }
    }

    /** A fresh token for a device that presented a valid one; the old token stops working. */
    fun rotateToken(deviceId: String): String? = synchronized(lock) {
        val devices = devices()
        val device = devices.firstOrNull { it.id == deviceId } ?: return null
        val token = Pairing.newToken()
        save(devices.map { if (it.id == device.id) device.copy(digest = Pairing.digest(token)) else it })
        token
    }

    fun devices(): List<Device> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Device(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    digest = item.optString("digest"),
                    createdAt = item.optLong("createdAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun revoke(deviceId: String) = synchronized(lock) {
        save(devices().filterNot { it.id == deviceId })
    }

    fun revokeAll() = synchronized(lock) {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(devices: List<Device>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("digest", device.digest)
                    .put("createdAt", device.createdAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "devices"
    }
}
