package dev.couchtv.launcher.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The codec is the contract between three codebases; these tests are the contract's guard rail.
 * Every message in `shared/remote-protocol.md` is decoded, and the awkward cases (unknown type,
 * unknown key, malformed JSON, oversized batches) are pinned down.
 */
class ProtocolTest {

    @Test
    fun `decodes hello with and without a token`() {
        val withToken = Protocol.decode(
            """{"t":"hello","v":1,"id":"abc","name":"Pixel","platform":"android","app":"Couch Remote/1.0","token":"xyz"}"""
        ) as Protocol.ClientMessage.Hello
        assertEquals("abc", withToken.id)
        assertEquals("xyz", withToken.token)
        assertEquals(1, withToken.version)

        val firstRun = Protocol.decode(
            """{"t":"hello","v":1,"id":"abc","name":"iPhone","platform":"ios","app":"Couch Remote/1.0"}"""
        ) as Protocol.ClientMessage.Hello
        assertNull(firstRun.token)
    }

    @Test
    fun `decodes every key the protocol defines`() {
        Protocol.RemoteKey.entries.forEach { key ->
            val message = Protocol.decode("""{"t":"key","k":"${key.wire}"}""")
            assertEquals(Protocol.ClientMessage.Key(key, false), message)
        }
    }

    @Test
    fun `unknown keys are ignored and unknown types are dropped`() {
        assertNull(Protocol.decode("""{"t":"teleport"}"""))
        assertNull(Protocol.decode("""{"t":"key","k":"launch_missiles"}"""))
        assertNull(Protocol.decode("not json"))
        assertNull(Protocol.decode("""{"no":"type"}"""))
        // A known message with extra keys still decodes.
        val hello = Protocol.decode("""{"t":"hello","v":1,"id":"a","name":"b","platform":"c","app":"d","future":42}""")
        assertTrue(hello is Protocol.ClientMessage.Hello)
    }

    @Test
    fun `pointer deltas are clamped to the screen`() {
        val fast = Protocol.decode("""{"t":"pointer","dx":9.5,"dy":-9.5}""") as Protocol.ClientMessage.Pointer
        assertEquals(1f, fast.dx, 0.0001f)
        assertEquals(-1f, fast.dy, 0.0001f)

        val missing = Protocol.decode("""{"t":"pointer"}""") as Protocol.ClientMessage.Pointer
        assertEquals(0f, missing.dx, 0.0001f)
    }

    @Test
    fun `pointer batches keep at most eight samples`() {
        val samples = (1..20).joinToString(",") { "[\"0.01\",\"0.02\"]" }
        val batch = Protocol.decode("""{"t":"pointer","batch":[$samples]}""") as Protocol.ClientMessage.PointerBatch
        assertEquals(Protocol.MAX_POINTER_BATCH, batch.samples.size)
        assertEquals(0.01f, batch.samples.first().dx, 0.0001f)
    }

    @Test
    fun `text is bounded and control messages survive`() {
        val long = "a".repeat(2_000)
        val text = Protocol.decode("""{"t":"text","v":"$long"}""") as Protocol.ClientMessage.Text
        assertEquals(Protocol.MAX_TEXT, text.value.length)

        assertEquals(Protocol.ClientMessage.Submit, Protocol.decode("""{"t":"submit"}"""))
        assertEquals(Protocol.ClientMessage.Backspace(3), Protocol.decode("""{"t":"backspace","n":3}"""))
        assertEquals(Protocol.ClientMessage.Backspace(32), Protocol.decode("""{"t":"backspace","n":999}"""))
        assertEquals(Protocol.ClientMessage.Click, Protocol.decode("""{"t":"click"}"""))
        assertEquals(
            Protocol.ClientMessage.Launch("com.netflix.mediaclient"),
            Protocol.decode("""{"t":"launch","pkg":"com.netflix.mediaclient"}"""),
        )
    }

    @Test
    fun `state encodes the fields the remote reads`() {
        val state = RemoteState(
            screen = "home",
            focusKind = "media",
            focusLabel = "The Bear",
            media = RemoteState.MediaState(true, "The Bear", "S2 E4", 1_000, 2_000, "com.example"),
            volume = 0.5f,
            muted = false,
            pointer = true,
            pin = false,
        )
        val json = Json.parseToJsonElement(Protocol.state(state)).jsonObject
        assertEquals("state", json["t"]?.jsonPrimitive?.content)
        assertEquals("home", json["screen"]?.jsonPrimitive?.content)
        assertEquals("The Bear", json["focus"]?.jsonObject?.get("label")?.jsonPrimitive?.content)
        assertEquals("media", json["focus"]?.jsonObject?.get("kind")?.jsonPrimitive?.content)
        assertEquals("S2 E4", json["media"]?.jsonObject?.get("subtitle")?.jsonPrimitive?.content)
        assertEquals("true", json["pointer"]?.jsonPrimitive?.content)
    }

    @Test
    fun `welcome advertises capabilities and a fresh token`() {
        val json = Json.parseToJsonElement(
            Protocol.welcome("dev1", "tok", "Living room", "Android TV", 1920, 1080)
        ).jsonObject
        assertEquals("welcome", json["t"]?.jsonPrimitive?.content)
        assertEquals("tok", json["token"]?.jsonPrimitive?.content)
        val caps = json["caps"]!!.toString()
        assertTrue(caps.contains("pointer"))
        assertTrue(caps.contains("voice"))
    }

    @Test
    fun `errors carry a code and optionally attempts`() {
        val json = Json.parseToJsonElement(Protocol.error("pin", "That code didn't work.", 2)).jsonObject
        assertEquals("pin", json["code"]?.jsonPrimitive?.content)
        assertEquals("2", json["attempts"]?.jsonPrimitive?.content)
    }

    @Test
    fun `paired is a welcome with a different type`() {
        val json = Json.parseToJsonElement(Protocol.paired("dev", "tok", "TV", "Android", 1, 1)).jsonObject
        assertEquals("paired", json["t"]?.jsonPrimitive?.content)
        assertEquals("tok", json["token"]?.jsonPrimitive?.content)
        assertFalse(json.containsKey("device") && json["device"] == null)
    }
}
