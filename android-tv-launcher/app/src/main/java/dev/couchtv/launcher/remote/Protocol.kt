package dev.couchtv.launcher.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The wire protocol, v1, exactly as documented in `shared/remote-protocol.md`.
 *
 * This file is the single place either side of the connection is defined, and it deliberately has no
 * Android imports: both the TV and the Android remote app can share the same source, and the whole
 * codec can be unit-tested on the JVM (`ProtocolTest`).
 *
 * Decoding is forgiving by contract — unknown message types and unknown keys are ignored, so a newer
 * remote can talk to an older TV.
 */
object Protocol {

    const val VERSION = 1

    sealed interface ClientMessage {
        data class Hello(
            val version: Int,
            val id: String,
            val name: String,
            val platform: String,
            val app: String,
            val token: String?,
        ) : ClientMessage

        data class Pair(val pin: String, val name: String, val platform: String, val id: String) : ClientMessage

        data class Key(val key: RemoteKey, val hold: Boolean) : ClientMessage

        /** One movement sample. `dx`/`dy` are fractions of the screen, per event. */
        data class Pointer(val dx: Float, val dy: Float) : ClientMessage

        /** Up to [MAX_POINTER_BATCH] samples, coalesced by the client into one frame. */
        data class PointerBatch(val samples: List<Sample>) : ClientMessage {
            data class Sample(val dx: Float, val dy: Float)
        }

        data object Click : ClientMessage
        data object LongPress : ClientMessage
        data class Scroll(val dy: Float) : ClientMessage
        data class Text(val value: String) : ClientMessage
        data class Backspace(val count: Int) : ClientMessage
        data object Submit : ClientMessage
        data class Voice(val text: String) : ClientMessage
        data class Launch(val packageName: String) : ClientMessage
        data class Command(val action: String, val arg: String?) : ClientMessage
        data class Ping(val ts: Long) : ClientMessage
    }

    /** Keys a remote can send. The names are the wire names; the mapping to key codes lives in InputRouter. */
    enum class RemoteKey(val wire: String) {
        UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"), OK("ok"),
        BACK("back"), HOME("home"), MENU("menu"), INFO("info"), SETTINGS("settings"), SEARCH("search"),
        PLAY("play"), PAUSE("pause"), PLAY_PAUSE("playpause"), STOP("stop"),
        REWIND("rewind"), FORWARD("forward"), NEXT("next"), PREV("prev"),
        VOLUME_UP("volup"), VOLUME_DOWN("voldown"), MUTE("mute"),
        POWER("power"), WAKE("wake"), SLEEP("sleep"),
        ;

        companion object {
            fun from(wire: String?): RemoteKey? = entries.firstOrNull { it.wire == wire }
        }
    }

    const val MAX_POINTER_BATCH = 8
    const val MAX_TEXT = 500
    const val MAX_BACKSPACE = 32

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses one frame. Returns null for anything that is not a valid v1 client message. */
    fun decode(text: String): ClientMessage? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = root.string("t") ?: return null
        return when (type) {
            "hello" -> ClientMessage.Hello(
                version = root.int("v") ?: VERSION,
                id = root.string("id").orEmpty(),
                name = root.string("name").orEmpty(),
                platform = root.string("platform").orEmpty(),
                app = root.string("app").orEmpty(),
                token = root.string("token")?.takeIf { it.isNotBlank() },
            )

            "pair" -> ClientMessage.Pair(
                pin = root.string("pin").orEmpty(),
                name = root.string("name").orEmpty(),
                platform = root.string("platform").orEmpty(),
                id = root.string("id").orEmpty(),
            )

            "key" -> RemoteKey.from(root.string("k"))?.let { key ->
                val hold = runCatching { root["hold"]?.jsonPrimitive?.booleanOrNull ?: false }
                    .getOrDefault(false)
                ClientMessage.Key(key, hold)
            }

            "pointer" -> {
                val batch = root["batch"] as? JsonArray
                if (batch != null) {
                    val samples = batch.take(MAX_POINTER_BATCH).mapNotNull { element ->
                        val sample = runCatching { element.jsonArray }.getOrNull() ?: return@mapNotNull null
                        if (sample.size < 2) return@mapNotNull null
                        val dx = runCatching { sample[0].jsonPrimitive.floatOrNull }.getOrNull() ?: return@mapNotNull null
                        val dy = runCatching { sample[1].jsonPrimitive.floatOrNull }.getOrNull() ?: return@mapNotNull null
                        ClientMessage.PointerBatch.Sample(dx.coerceIn(-1f, 1f), dy.coerceIn(-1f, 1f))
                    }
                    if (samples.isEmpty()) null else ClientMessage.PointerBatch(samples)
                } else {
                    val dx = root.float("dx") ?: 0f
                    val dy = root.float("dy") ?: 0f
                    ClientMessage.Pointer(dx.coerceIn(-1f, 1f), dy.coerceIn(-1f, 1f))
                }
            }

            "click" -> ClientMessage.Click
            "longpress" -> ClientMessage.LongPress
            "scroll" -> ClientMessage.Scroll(root.float("dy") ?: 0f)
            "text" -> root.string("v")?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() }?.let(ClientMessage::Text)
            "backspace" -> ClientMessage.Backspace((root.int("n") ?: 1).coerceIn(1, MAX_BACKSPACE))
            "submit" -> ClientMessage.Submit
            "voice" -> root.string("text")?.take(MAX_TEXT)?.takeIf { it.isNotBlank() }?.let(ClientMessage::Voice)
            "launch" -> root.string("pkg")?.takeIf { it.isNotBlank() }?.let(ClientMessage::Launch)
            "command" -> root.string("a")?.let { action ->
                ClientMessage.Command(action, root.string("q"))
            }

            "ping" -> ClientMessage.Ping(root.long("ts") ?: 0L)
            else -> null // Unknown types are ignored, by contract.
        }
    }

    // ---- Encoding (TV → client) ---------------------------------------------------------------

    private const val CAPS = "dpad,pointer,text,voice,apps,media,power,volume"

    fun welcome(
        deviceId: String,
        token: String,
        tvName: String,
        tvModel: String,
        widthPx: Int,
        heightPx: Int,
    ): String = buildJsonObject {
        put("t", "welcome")
        put("v", VERSION)
        put("device", deviceId)
        put("token", token)
        putJsonObject("tv") {
            put("name", tvName)
            put("model", tvModel)
            put("w", widthPx)
            put("h", heightPx)
        }
        putJsonArray("caps") { CAPS.split(',').forEach { add(it) } }
    }.toString()

    fun pairRequired(deviceId: String, tvName: String, pinLength: Int): String = buildJsonObject {
        put("t", "pair-required")
        put("device", deviceId)
        put("tvName", tvName)
        put("pin", pinLength)
    }.toString()

    fun paired(deviceId: String, token: String, tvName: String, tvModel: String, widthPx: Int, heightPx: Int): String {
        val base = Json.parseToJsonElement(welcome(deviceId, token, tvName, tvModel, widthPx, heightPx)).jsonObject
        return buildJsonObject {
            put("t", "paired")
            base.forEach { (key, value) -> if (key != "t") put(key, value) }
        }.toString()
    }

    fun error(code: String, message: String, attempts: Int? = null): String = buildJsonObject {
        put("t", "error")
        put("code", code)
        put("message", message)
        attempts?.let { put("attempts", it) }
    }.toString()

    fun pong(ts: Long, tv: Long): String = buildJsonObject {
        put("t", "pong")
        put("ts", ts)
        put("tv", tv)
    }.toString()

    fun toast(text: String): String = buildJsonObject {
        put("t", "toast")
        put("text", text)
    }.toString()

    fun event(name: String): String = buildJsonObject {
        put("t", "event")
        put("name", name)
    }.toString()

    fun state(state: RemoteState): String = buildJsonObject {
        put("t", "state")
        put("screen", state.screen)
        if (state.focusLabel != null) {
            putJsonObject("focus") {
                put("kind", state.focusKind ?: "item")
                put("label", state.focusLabel)
            }
        } else {
            put("focus", JsonNull)
        }
        val media = state.media
        if (media == null) {
            put("media", JsonNull)
        } else {
            putJsonObject("media") {
                put("playing", media.playing)
                put("title", media.title)
                media.subtitle?.let { put("subtitle", it) }
                put("positionMs", media.positionMs)
                put("durationMs", media.durationMs)
                media.app?.let { put("app", it) }
            }
        }
        put("volume", state.volume)
        put("muted", state.muted)
        put("pointer", state.pointer)
        put("pin", state.pin)
    }.toString()

    fun apps(apps: List<RemoteApp>): String = buildJsonObject {
        put("t", "apps")
        putJsonArray("apps") {
            apps.forEach { app ->
                add(
                    buildJsonObject {
                        put("pkg", app.packageName)
                        put("name", app.name)
                        put("color", app.color)
                        put("fav", app.favourite)
                    }
                )
            }
        }
    }.toString()

    /** A snapshot the TV answers a client's `state`-less hello with, if a client ever asks. */
    fun capabilities(): String = buildJsonObject {
        put("t", "caps")
        putJsonArray("caps") { CAPS.split(',').forEach { add(it) } }
        put("v", VERSION)
    }.toString()

    // ---- Small JSON helpers ---------------------------------------------------------------------

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() }.getOrNull()

    private fun JsonObject.float(key: String): Float? =
        runCatching { this[key]?.jsonPrimitive?.floatOrNull }
            .getOrNull()
            ?: runCatching { this[key]?.jsonPrimitive?.doubleOrNull?.toFloat() }.getOrNull()

    /** Kept for callers that need to inspect a raw frame (tests, diagnostics). */
    fun peekType(text: String): String? =
        runCatching { json.parseToJsonElement(text).jsonObject.string("t") }.getOrNull()

    /** Serialises a batch of pointer samples, for tests and for the client implementation. */
    fun encodePointerBatch(samples: List<ClientMessage.PointerBatch.Sample>): String = buildJsonObject {
        put("t", "pointer")
        put(
            "batch",
            buildJsonArray {
                samples.take(MAX_POINTER_BATCH).forEach { sample ->
                    add(buildJsonArray { add(sample.dx); add(sample.dy) })
                }
            }
        )
    }.toString()

    fun encodePointer(dx: Float, dy: Float, ts: Long? = null): String = buildJsonObject {
        put("t", "pointer")
        put("dx", dx)
        put("dy", dy)
        ts?.let { put("ts", it) }
    }.toString()

    fun encodeKey(key: RemoteKey, hold: Boolean = false): String = buildJsonObject {
        put("t", "key")
        put("k", key.wire)
        if (hold) put("hold", true)
    }.toString()

    fun encodeText(value: String): String = buildJsonObject {
        put("t", "text")
        put("v", value)
    }.toString()

    fun encodeHello(id: String, name: String, platform: String, app: String, token: String?): String =
        buildJsonObject {
            put("t", "hello")
            put("v", VERSION)
            put("id", id)
            put("name", name)
            put("platform", platform)
            put("app", app)
            token?.let { put("token", it) }
        }.toString()

    fun encodePair(pin: String, id: String, name: String, platform: String): String = buildJsonObject {
        put("t", "pair")
        put("pin", pin)
        put("id", id)
        put("name", name)
        put("platform", platform)
    }.toString()
}

/** What the TV is doing, for the remote's contextual controls. */
data class RemoteState(
    val screen: String = "home",
    val focusKind: String? = null,
    val focusLabel: String? = null,
    val media: MediaState? = null,
    val volume: Float = 0.4f,
    val muted: Boolean = false,
    val pointer: Boolean = false,
    val pin: Boolean = false,
) {
    data class MediaState(
        val playing: Boolean,
        val title: String,
        val subtitle: String?,
        val positionMs: Long,
        val durationMs: Long,
        val app: String?,
    )
}

/** One entry of the remote's app shortcuts. */
data class RemoteApp(val packageName: String, val name: String, val color: String, val favourite: Boolean)

/** The surface the TV implements so the server can deliver input to the running UI. */
interface RemoteInputSink {
    /** Returns true when the launcher used the key; false lets the server try the system. */
    fun onKey(key: Protocol.RemoteKey, hold: Boolean): Boolean

    fun onPointer(dx: Float, dy: Float)
    fun onClick()
    fun onLongPress()
    fun onScroll(dy: Float)
    fun onText(text: String)
    fun onBackspace(count: Int)
    fun onLaunch(packageName: String): Boolean
    fun onCommand(action: String, arg: String?): Boolean

    /** For HTTP: an icon PNG for a package, or null. */
    fun iconFor(packageName: String): ByteArray? = null
}
