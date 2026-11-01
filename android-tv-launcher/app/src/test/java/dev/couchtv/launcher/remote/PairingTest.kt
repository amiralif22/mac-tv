package dev.couchtv.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {

    @Test
    fun `codes are six digits and rotate on a schedule`() {
        val code = Pairing.newCode(now = 1_000L)
        assertEquals(6, code.value.length)
        assertTrue(code.value.all { it.isDigit() })
        assertFalse(code.isExpired(1_000L))
        assertTrue(code.isExpired(1_000L + Pairing.CODE_TTL_MS))
    }

    @Test
    fun `a new code never equals the one on screen`() {
        var previous: String? = null
        repeat(50) {
            val code = Pairing.newCode(previous = previous)
            assertNotEquals(previous, code.value)
            previous = code.value
        }
    }

    @Test
    fun `tokens are url safe, long enough, and digest consistently`() {
        val token = Pairing.newToken()
        assertEquals(32, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(Pairing.digest(token), Pairing.digest(token))
        assertEquals(64, Pairing.digest(token).length)
        assertNotEquals(Pairing.digest(token), Pairing.digest(Pairing.newToken()))
    }

    @Test
    fun `comparison is length aware and value exact`() {
        assertTrue(Pairing.constantTimeEquals("abc", "abc"))
        assertFalse(Pairing.constantTimeEquals("abc", "abd"))
        assertFalse(Pairing.constantTimeEquals("abc", "abcd"))
        assertFalse(Pairing.constantTimeEquals("", "a"))
    }

    @Test
    fun `only six digit pins are well formed`() {
        assertTrue(Pairing.isWellFormedPin("123456"))
        assertFalse(Pairing.isWellFormedPin("12345"))
        assertFalse(Pairing.isWellFormedPin("12345a"))
        assertFalse(Pairing.isWellFormedPin(""))
    }

    @Test
    fun `five wrong pins block further attempts for ten minutes`() {
        val attempts = Pairing.Attempts()
        repeat(4) { attempts.record(1_000L) }
        assertFalse(attempts.isBlocked(1_000L))
        assertEquals(1, attempts.remaining())
        attempts.record(1_000L)
        assertTrue(attempts.isBlocked(1_000L))
        // The window slides.
        assertFalse(attempts.isBlocked(1_000L + Pairing.ATTEMPT_WINDOW_MS + 1))
    }
}
