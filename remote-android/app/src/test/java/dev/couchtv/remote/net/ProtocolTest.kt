package dev.couchtv.remote.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's half of the protocol. If this app and the TV disagree, nothing works, so every frame
 * it can produce and every frame the TV can send is pinned here.
 */
class ProtocolTest {

    @Test
    fun `hello carries no token on a first connect`() {
        val json = Json.parseToJsonElement(
            Protocol.hello("abc", "Pixel 8", "android", "Couch Remote/1.0", token = null)
        ).jsonObject
        assertNull(json["token"])
        assertEquals("hello", json["t"]?.jsonPrimitive?.content)
        assertEquals("1", json["v"]?.jsonPrimitive?.content)
    }

    @Test
    fun `every key sends its wire name`() {
        Protocol.Key.entries.forEach { key ->
            val json = Json.parseToJsonElement(Protocol.key(key)).jsonObject
            assertEquals(key.wire, json["k"]?.jsonPrimitive?.content)
            assertNull(json["hold"])
        }
        val held = Json.parseToJsonElement(Protocol.key(Protocol.Key.OK, hold = true)).jsonObject
        assertEquals("true", held["hold"]?.jsonPrimitive?.content)
    }

    @Test
    fun `pointer deltas are clamped and batches are capped`() {
        val pointer = Json.parseToJsonElement(Protocol.pointer(9.5f, -9.5f, ts = 7)).jsonObject
        // JUnit's float overload needs non-null floats; a missing field shows up as NaN.
        val dx = pointer["dx"]?.jsonPrimitive?.content?.toFloat() ?: Float.NaN
        val dy = pointer["dy"]?.jsonPrimitive?.content?.toFloat() ?: Float.NaN
        assertEquals(1f, dx, 0.0001f)
        assertEquals(-1f, dy, 0.0001f)

        val samples = (1..20).map { Protocol.Sample(dx = 0.01f, dy = 0.02f, ts = it.toLong()) }
        val batch = Json.parseToJsonElement(Protocol.pointerBatch(samples)).jsonObject
        val entries = batch["batch"]?.toString().orEmpty()
        // At most eight samples: one pair of numbers each, plus the outer brackets.
        assertEquals(Protocol.MAX_POINTER_BATCH, entries.split("[").size - 2)
    }

    @Test
    fun `text is bounded and empty text is not sent`() {
        assertNull(Protocol.text(""))
        val long = Protocol.text("a".repeat(900))!!
        val parsed = Json.parseToJsonElement(long).jsonObject
        assertEquals(Protocol.MAX_TEXT, parsed["v"]?.jsonPrimitive?.content?.length)

        val none = Json.parseToJsonElement(Protocol.backspace(0)).jsonObject
        assertEquals(1, none["n"]?.jsonPrimitive?.content?.toInt())
        val many = Json.parseToJsonElement(Protocol.backspace(999)).jsonObject
        assertEquals(Protocol.MAX_BACKSPACE, many["n"]?.jsonPrimitive?.content?.toInt())

        assertNull(Protocol.launch(" "))
        assertNull(Protocol.voice("  "))
        assertNotNull(Protocol.voice("the bear"))
    }

    @Test
    fun `decodes a welcome with the TV geometry`() {
        val frame = """{"t":"welcome","v":1,"device":"d1","token":"tok",""" +
            """"tv":{"name":"Living room","model":"Android TV","w":1920,"h":1080},"caps":["dpad","pointer"]}"""
        val message = Protocol.decode(frame) as Protocol.ServerMessage.Welcome
        assertEquals("tok", message.token)
        assertEquals("Living room", message.name)
        assertEquals(1920, message.width)
        assertTrue(message.caps.contains("pointer"))
    }

    @Test
    fun `decodes pair-required, errors and state`() {
        val pair = Protocol.decode("""{"t":"pair-required","device":"d1","tvName":"TV","pin":6}""")
        assertEquals(Protocol.ServerMessage.PairRequired("d1", "TV", 6), pair)

        val error = Protocol.decode("""{"t":"error","code":"pin","message":"Nope","attempts":2}""")
        assertEquals(Protocol.ServerMessage.Error("pin", "Nope", 2), error)

        val frame = """{"t":"state","screen":"home","focus":{"kind":"app","label":"Netflix"},""" +
            """"volume":0.4,"muted":false,"pointer":true,"pin":false}"""
        val state = Protocol.decode(frame) as Protocol.ServerMessage.State
        assertEquals("home", state.state.screen)
        assertEquals("Netflix", state.state.focusLabel)
        assertEquals(0.4f, state.state.volume!!, 0.0001f)
        assertEquals(true, state.state.pointer)
        assertNull(state.state.media)
    }

    @Test
    fun `decodes the app list and tolerates junk`() {
        val frame = """{"t":"apps","apps":[{"pkg":"com.netflix.mediaclient","name":"Netflix",""" +
            """"color":"#E50914","fav":true},{"nopkg":true}]}"""
        val apps = Protocol.decode(frame) as Protocol.ServerMessage.Apps
        assertEquals(1, apps.apps.size)
        assertEquals("com.netflix.mediaclient", apps.apps.first().packageName)
        assertTrue(apps.apps.first().favourite)

        assertNull(Protocol.decode("""{"t":"teleport"}"""))
        assertNull(Protocol.decode("not json"))
        assertNull(Protocol.decode("""{"t":"welcome","device":"x"}"""))
        assertNotNull(Protocol.decode("""{"t":"toast","text":"hi"}"""))
    }
}
