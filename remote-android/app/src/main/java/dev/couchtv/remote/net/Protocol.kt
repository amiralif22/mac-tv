package dev.couchtv.remote.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * The client half of remote protocol v1, mirroring `shared/remote-protocol.md`.
 *
 * It is a copy of the TV's codec rather than a shared module on purpose: the two apps are separate
 * Gradle builds that ship on their own schedules, and a shared `:protocol` module would couple their
 * releases. The specification is the contract; both copies are tested against it.
 */
object Protocol {

    const val VERSION = 1
    const val MAX_TEXT = 500
    const val MAX_BACKSPACE = 32
    const val MAX_POINTER_BATCH = 8

    enum class Key(val wire: String) {
        UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"), OK("ok"),
        BACK("back"), HOME("home"), MENU("menu"), INFO("info"), SETTINGS("settings"), SEARCH("search"),
        PLAY("play"), PAUSE("pause"), PLAY_PAUSE("playpause"), STOP("stop"),
        REWIND("rewind"), FORWARD("forward"), NEXT("next"), PREV("prev"),
        VOLUME_UP("volup"), VOLUME_DOWN("voldown"), MUTE("mute"),
        POWER("power"), WAKE("wake"), SLEEP("sleep"),
    }

    data class Sample(val dx: Float, val dy: Float, val ts: Long = System.currentTimeMillis())

    // ---- Client → TV ---------------------------------------------------------------------------

    fun hello(deviceId: String, name: String, platform: String, app: String, token: String?): String =
        buildJsonObject {
            put("t", "hello")
            put("v", VERSION)
            put("id", deviceId)
            put("name", name)
            put("platform", platform)
            put("app", app)
            if (!token.isNullOrEmpty()) put("token", token)
        }.toString()

    fun pair(pin: String, deviceId: String, name: String, platform: String): String = buildJsonObject {
        put("t", "pair")
        put("pin", pin.filter { it.isDigit() })
        put("id", deviceId)
        put("name", name)
        put("platform", platform)
    }.toString()

    fun key(key: Key, hold: Boolean = false): String = buildJsonObject {
        put("t", "key")
        put("k", key.wire)
        if (hold) put("hold", true)
    }.toString()

    fun pointer(dx: Float, dy: Float, ts: Long = System.currentTimeMillis()): String = buildJsonObject {
        put("t", "pointer")
        put("dx", dx.coerceIn(-1f, 1f))
        put("dy", dy.coerceIn(-1f, 1f))
        put("ts", ts)
    }.toString()

    /** One message per display frame, at most [MAX_POINTER_BATCH] samples. */
    fun pointerBatch(samples: List<Sample>): String = buildJsonObject {
        put("t", "pointer")
        putJsonArray("batch") {
            samples.takeLast(MAX_POINTER_BATCH).forEach { sample ->
                add(
                    buildJsonArray {
                        add(sample.dx.coerceIn(-1f, 1f))
                        add(sample.dy.coerceIn(-1f, 1f))
                        add(sample.ts)
                    }
                )
            }
        }
    }.toString()

    fun click(): String = """{"t":"click"}"""
    fun longPress(): String = """{"t":"longpress"}"""
    fun scroll(dy: Float): String = buildJsonObject { put("t", "scroll"); put("dy", dy) }.toString()

    fun text(value: String): String? =
        value.take(MAX_TEXT).takeIf { it.isNotEmpty() }?.let {
            buildJsonObject { put("t", "text"); put("v", it) }.toString()
        }

    fun backspace(count: Int = 1): String = buildJsonObject {
        put("t", "backspace")
        put("n", count.coerceIn(1, MAX_BACKSPACE))
    }.toString()

    fun submit(): String = """{"t":"submit"}"""

    fun voice(transcription: String, final: Boolean = true): String? =
        transcription.take(MAX_TEXT).takeIf { it.isNotBlank() }?.let {
            buildJsonObject {
                put("t", "voice")
                put("text", it)
                put("final", final)
            }.toString()
        }

    fun launch(packageName: String): String? = packageName.takeIf { it.isNotBlank() }?.let {
        buildJsonObject { put("t", "launch"); put("pkg", it) }.toString()
    }

    fun command(action: String, query: String? = null): String = buildJsonObject {
        put("t", "command")
        put("a", action)
        if (!query.isNullOrEmpty()) put("q", query)
    }.toString()

    fun ping(ts: Long = System.currentTimeMillis()): String =
        buildJsonObject { put("t", "ping"); put("ts", ts) }.toString()

    // ---- TV → client ---------------------------------------------------------------------------

    sealed interface ServerMessage {
        data class Welcome(
            val device: String,
            val token: String,
            val name: String,
            val model: String,
            val width: Int,
            val height: Int,
            val caps: List<String>,
        ) : ServerMessage

        data class Paired(val welcome: Welcome) : ServerMessage
        data class PairRequired(val device: String, val tvName: String, val digits: Int) : ServerMessage
        data class Error(val code: String, val message: String?, val attempts: Int?) : ServerMessage
        data class State(val state: TvState) : ServerMessage
        data class Apps(val apps: List<TvApp>) : ServerMessage
        data class Toast(val text: String) : ServerMessage
        data class Event(val name: String) : ServerMessage
        data class Pong(val ts: Long, val tv: Long?) : ServerMessage
    }

    data class TvState(
        val screen: String? = null,
        val focusLabel: String? = null,
        val media: Media? = null,
        val volume: Float? = null,
        val muted: Boolean? = null,
        val pointer: Boolean? = null,
        val pin: Boolean? = null,
    ) {
        data class Media(
            val playing: Boolean,
            val title: String,
            val subtitle: String?,
            val positionMs: Long,
            val durationMs: Long,
        )
    }

    data class TvApp(
        val packageName: String,
        val name: String,
        val color: String,
        val favourite: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Returns null for anything that is not a v1 server message; callers ignore null. */
    fun decode(text: String): ServerMessage? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return when (root.string("t")) {
            "welcome" -> welcome(root)?.let { ServerMessage.Welcome(it) }
            "paired" -> welcome(root)?.let { ServerMessage.Paired(it) }
            "pair-required" -> ServerMessage.PairRequired(
                device = root.string("device").orEmpty(),
                tvName = root.string("tvName") ?: "TV",
                digits = root.int("pin") ?: 6,
            )
            "error" -> ServerMessage.Error(
                code = root.string("code") ?: "internal",
                message = root.string("message"),
                attempts = root.int("attempts"),
            )
            "state" -> ServerMessage.State(state(root))
            "apps" -> ServerMessage.Apps(apps(root))
            "toast" -> root.string("text")?.let(ServerMessage::Toast)
            "event" -> root.string("name")?.let(ServerMessage::Event)
            "pong" -> ServerMessage.Pong(root.long("ts") ?: 0L, root.long("tv"))
            else -> null // Unknown types are ignored by contract.
        }
    }

    private fun welcome(root: JsonObject): ServerMessage.Welcome? {
        val token = root.string("token")?.takeIf { it.isNotEmpty() } ?: return null
        val tv = runCatching { root["tv"]?.jsonObject }.getOrNull()
        return ServerMessage.Welcome(
            device = root.string("device").orEmpty(),
            token = token,
            name = tv?.string("name") ?: "TV",
            model = tv?.string("model").orEmpty(),
            width = tv?.int("w") ?: 1920,
            height = tv?.int("h") ?: 1080,
            caps = root["caps"]?.let { element ->
                runCatching { element.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
            }.orEmpty(),
        )
    }

    private fun state(root: JsonObject): TvState {
        val focus = runCatching { root["focus"]?.jsonObject }.getOrNull()
        val media = runCatching { root["media"]?.jsonObject }.getOrNull()?.let { entry ->
            val title = entry.string("title") ?: return@let null
            TvState.Media(
                playing = entry.boolean("playing") ?: false,
                title = title,
                subtitle = entry.string("subtitle"),
                positionMs = entry.long("positionMs") ?: 0L,
                durationMs = entry.long("durationMs") ?: 0L,
            )
        }
        return TvState(
            screen = root.string("screen"),
            focusLabel = focus?.string("label"),
            media = media,
            volume = runCatching { root["volume"]?.jsonPrimitive?.floatOrNull }.getOrNull(),
            muted = root.boolean("muted"),
            pointer = root.boolean("pointer"),
            pin = root.boolean("pin"),
        )
    }

    private fun apps(root: JsonObject): List<TvApp> = runCatching {
        root["apps"]?.jsonArray?.mapNotNull { raw ->
            val entry = runCatching { raw.jsonObject }.getOrNull() ?: return@mapNotNull null
            val pkg = entry.string("pkg")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            TvApp(
                packageName = pkg,
                name = entry.string("name") ?: pkg,
                color = entry.string("color") ?: "#6E7482",
                favourite = entry.boolean("fav") ?: false,
            )
        }.orEmpty()
    }.getOrDefault(emptyList())

    // Small helpers: the codec above reads like the specification because of them.
    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

}
