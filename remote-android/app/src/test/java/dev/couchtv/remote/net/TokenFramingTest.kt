package dev.couchtv.remote.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Keystore only exists on a device, but the layout of a stored token does not: it is an
 * initialisation vector followed by its ciphertext, and a mistake there would make every pairing
 * silently irrecoverable.
 */
class TokenFramingTest {

    private val iv = ByteArray(TokenFraming.IV_BYTES) { it.toByte() }
    private val body = byteArrayOf(10, 20, 30, 40, 50)

    @Test
    fun `the vector is stored in front of the body`() {
        val packed = TokenFraming.pack(iv, body)
        assertEquals(iv.size + body.size, packed.size)
        assertArrayEquals(iv, packed.copyOfRange(0, iv.size))
        assertArrayEquals(body, packed.copyOfRange(iv.size, packed.size))
    }

    @Test
    fun `packing and splitting round trip`() {
        val (readIv, readBody) = TokenFraming.split(TokenFraming.pack(iv, body))!!
        assertArrayEquals(iv, readIv)
        assertArrayEquals(body, readBody)
    }

    @Test
    fun `an empty body still round trips`() {
        val split = TokenFraming.split(TokenFraming.pack(iv, ByteArray(0)))
        assertNotNull(split)
        assertArrayEquals(iv, split!!.first)
        assertEquals(0, split.second.size)
    }

    @Test
    fun `a truncated blob is rejected`() {
        assertNull(TokenFraming.split(ByteArray(0)))
        assertNull(TokenFraming.split(ByteArray(TokenFraming.IV_BYTES - 1)))
    }

    @Test
    fun `a full length blob with a one byte body is accepted`() {
        val split = TokenFraming.split(TokenFraming.pack(iv, byteArrayOf(7)))
        assertNotNull(split)
        assertEquals(1, split!!.second.size)
    }
}
