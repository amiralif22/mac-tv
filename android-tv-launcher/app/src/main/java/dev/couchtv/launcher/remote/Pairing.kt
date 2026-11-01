package dev.couchtv.launcher.remote

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Pairing arithmetic, with no Android in it so it can be tested: the six-digit code shown on the TV,
 * the tokens handed to phones, and the digests stored on disk.
 *
 * Mirrors the Mac app's `PairingStore`: a code that rotates every five minutes, tokens generated from
 * a CSPRNG, only SHA-256 digests persisted, and constant-time comparison so a remote cannot learn a
 * token one byte at a time.
 */
object Pairing {
    const val CODE_DIGITS = 6
    const val CODE_TTL_MS = 5L * 60 * 1000
    const val TOKEN_BYTES = 24 // 32 URL-safe characters
    const val MAX_ATTEMPTS = 5
    const val ATTEMPT_WINDOW_MS = 10L * 60 * 1000

    data class Code(val value: String, val issuedAt: Long, val expiresAt: Long) {
        fun isExpired(now: Long): Boolean = now >= expiresAt
    }

    private val random = SecureRandom()

    fun newCode(now: Long = System.currentTimeMillis(), previous: String? = null): Code {
        var value: String
        do {
            value = String.format("%06d", random.nextInt(1_000_000))
        } while (value == previous)
        return Code(value, now, now + CODE_TTL_MS)
    }

    fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun digest(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Length-independent, value-safe comparison. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) difference = difference or (left[index].toInt() xor right[index].toInt())
        return difference == 0
    }

    fun isWellFormedPin(pin: String): Boolean =
        pin.length == CODE_DIGITS && pin.all { it.isDigit() }

    /** Throttles guessing: five wrong codes from one address, then a ten-minute pause. */
    class Attempts {
        private val failures = ArrayDeque<Long>()

        fun record(now: Long = System.currentTimeMillis()) {
            failures.addLast(now)
            while (failures.isNotEmpty() && now - failures.first() > ATTEMPT_WINDOW_MS) failures.removeFirst()
        }

        fun isBlocked(now: Long = System.currentTimeMillis()): Boolean {
            while (failures.isNotEmpty() && now - failures.first() > ATTEMPT_WINDOW_MS) failures.removeFirst()
            return failures.size >= MAX_ATTEMPTS
        }

        fun remaining(): Int = (MAX_ATTEMPTS - failures.size).coerceAtLeast(0)

        fun clear() = failures.clear()
    }
}
