package dev.couchtv.remote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Manual entry is the fallback when multicast is blocked, so it has to be forgiving about what a
 * person types and strict about what it hands to a socket.
 */
class DiscoveryAddressTest {

    @Test
    fun `a bare address keeps the default port`() {
        val parsed = parseManualAddress("192.168.1.42")
        assertNotNull(parsed)
        assertEquals("192.168.1.42", parsed!!.host)
        assertEquals(DEFAULT_REMOTE_PORT, parsed.port)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        val parsed = parseManualAddress("   192.168.1.42:9000 \n")
        assertNotNull(parsed)
        assertEquals("192.168.1.42", parsed!!.host)
        assertEquals(9000, parsed.port)
    }

    @Test
    fun `hostnames are accepted`() {
        val parsed = parseManualAddress("tv.local:8765")
        assertNotNull(parsed)
        assertEquals("tv.local", parsed!!.host)
        assertEquals(8765, parsed.port)
    }

    @Test
    fun `bracketed ipv6 keeps its brackets out of the host`() {
        val parsed = parseManualAddress("[fe80::1]:8765")
        assertNotNull(parsed)
        assertEquals("fe80::1", parsed!!.host)
        assertEquals(8765, parsed.port)
    }

    @Test
    fun `bracketed ipv6 without a port keeps the default`() {
        val parsed = parseManualAddress("[fe80::1]")
        assertNotNull(parsed)
        assertEquals("fe80::1", parsed!!.host)
        assertEquals(DEFAULT_REMOTE_PORT, parsed.port)
    }

    @Test
    fun `a bare ipv6 literal is read as a host, not as host and port`() {
        // Four colons cannot be an address/port pair, so the whole string is the host.
        val parsed = parseManualAddress("fe80::1")
        assertNotNull(parsed)
        assertEquals("fe80::1", parsed!!.host)
        assertEquals(DEFAULT_REMOTE_PORT, parsed.port)
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(parseManualAddress(""))
        assertNull(parseManualAddress("   "))
    }

    @Test
    fun `a broken port is rejected`() {
        assertNull(parseManualAddress("192.168.1.42:"))
        assertNull(parseManualAddress("192.168.1.42:abc"))
        assertNull(parseManualAddress("192.168.1.42:-1"))
        assertNull(parseManualAddress("192.168.1.42:1.5"))
    }

    @Test
    fun `out of range ports are rejected`() {
        assertNull(parseManualAddress("192.168.1.42:0"))
        assertNull(parseManualAddress("192.168.1.42:65536"))
    }

    @Test
    fun `a half written ipv6 bracket is rejected`() {
        assertNull(parseManualAddress("[fe80::1"))
        assertNull(parseManualAddress("[fe80::1]8765"))
    }

    @Test
    fun `a host with no name is rejected`() {
        assertNull(parseManualAddress(":8765"))
    }

    @Test
    fun `a host containing a space is rejected`() {
        assertNull(parseManualAddress("tv box:8765"))
    }

    @Test
    fun `a manual tv builds usable urls`() {
        val tv = DiscoveredTv(
            id = "manual:192.168.1.42:8765",
            name = "192.168.1.42",
            model = "Entered by hand",
            host = "192.168.1.42",
            port = 8765,
            pairing = false,
            manual = true,
        )
        assertEquals("192.168.1.42:8765", tv.address)
        assertEquals("ws://192.168.1.42:8765/remote", tv.websocketUrl)
        assertEquals("http://192.168.1.42:8765/icon?pkg=com.example", tv.iconUrl("com.example"))
    }

    @Test
    fun `an ipv6 tv brackets its host inside urls`() {
        val tv = DiscoveredTv(
            id = "manual:fe80::1:8765",
            name = "fe80::1",
            model = "Entered by hand",
            host = "fe80::1",
            port = 8765,
            pairing = false,
            manual = true,
        )
        assertEquals("ws://[fe80::1]:8765/remote", tv.websocketUrl)
        assertEquals("http://[fe80::1]:8765/icon?pkg=com.example", tv.iconUrl("com.example"))
    }
}
