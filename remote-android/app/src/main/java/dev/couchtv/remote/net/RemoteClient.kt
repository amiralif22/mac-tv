package dev.couchtv.remote.net

import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * One WebSocket link to one TV: handshake, pairing, input, state and keep-alive.
 *
 * Two design points worth naming:
 *
 * * **Callbacks land on the main thread.** The library calls back on its own threads; every message
 *   is posted to the main looper so nothing in the UI has to synchronise.
 * * **Sends are fire-and-forget.** A tap must not wait for the network. If the socket dies, the
 *   receive path notices and reconnects; the gesture is simply lost, as it would be on a real remote.
 */
class RemoteClient(
    private val deviceId: String,
    private val deviceName: String,
    private val vault: TokenVault,
    private val appVersion: String,
    private val listener: Listener,
) {

    interface Listener {
        fun onLinkState(state: LinkState)
        fun onServerMessage(message: Protocol.ServerMessage)
        fun onError(message: String)
    }

    sealed interface LinkState {
        data object Idle : LinkState
        data object Connecting : LinkState
        data object Handshaking : LinkState
        data object AwaitingPairing : LinkState
        data class Connected(val tvName: String) : LinkState
        data class Reconnecting(val attempt: Int) : LinkState
        data class Failed(val message: String) : LinkState
    }

    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocketClient? = null
    private var heartbeat: ScheduledExecutorService? = null
    private var tv: DiscoveredTv? = null
    private var attempt = 0
    private var wantsConnection = false
    private var reconnecting = false

    val currentTv: DiscoveredTv? get() = tv

    fun connect(tv: DiscoveredTv) {
        this.tv = tv
        wantsConnection = true
        attempt = 0
        open()
    }

    fun disconnect() {
        wantsConnection = false
        stopHeartbeat()
        closeSocket()
        post { listener.onLinkState(LinkState.Idle) }
    }

    /** An immediate retry, used when the app returns to the foreground. */
    fun retryNow() {
        if (!wantsConnection) return
        if (isOpen()) return
        attempt = 0
        open()
    }

    // ---- Link ---------------------------------------------------------------------------------

    private fun open() {
        val target = tv ?: return
        closeSocket()
        post { listener.onLinkState(LinkState.Connecting) }

        val client = object : WebSocketClient(URI(target.websocketUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                attempt = 0
                sendFrame(
                    Protocol.hello(
                        deviceId = deviceId,
                        name = deviceName,
                        platform = "android",
                        app = "Couch Remote/$appVersion",
                        token = vault.token(target.id),
                    )
                )
                startHeartbeat()
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                val decoded = Protocol.decode(message) ?: return
                post { handle(decoded) }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                // The close callback follows, so the reconnect is scheduled there, once.
                post { listener.onError("The TV stopped responding.") }
            }
        }

        client.connectionLostTimeout = 45
        socket = client
        runCatching { client.connect() }
            .onFailure {
                post { listener.onLinkState(LinkState.Failed("Could not reach that address.")) }
                wantsConnection = false
            }
    }

    private fun handle(message: Protocol.ServerMessage) {
        when (message) {
            is Protocol.ServerMessage.Welcome -> acceptPairing(message)
            is Protocol.ServerMessage.Paired -> acceptPairing(
                Protocol.ServerMessage.Welcome(
                    device = message.welcome.device,
                    token = message.welcome.token,
                    name = message.welcome.name,
                    model = message.welcome.model,
                    width = message.welcome.width,
                    height = message.welcome.height,
                    caps = message.welcome.caps,
                )
            )

            is Protocol.ServerMessage.PairRequired -> listener.onLinkState(LinkState.AwaitingPairing)

            is Protocol.ServerMessage.Error -> {
                listener.onError(message.message ?: friendly(message.code))
                when (message.code) {
                    "auth" -> {
                        tv?.let { vault.clear(it.id) }
                        wantsConnection = false
                        listener.onLinkState(LinkState.AwaitingPairing)
                    }
                    "pin" -> listener.onLinkState(LinkState.AwaitingPairing)
                    "busy" -> {
                        wantsConnection = false
                        listener.onLinkState(LinkState.Failed("The TV is busy. Try again shortly."))
                    }
                    else -> listener.onLinkState(LinkState.Failed(friendly(message.code)))
                }
            }

            else -> listener.onServerMessage(message)
        }
    }

    private fun acceptPairing(welcome: Protocol.ServerMessage.Welcome) {
        val id = welcome.device.ifEmpty { tv?.id.orEmpty() }
        if (id.isNotEmpty()) vault.save(id, welcome.token)
        attempt = 0
        listener.onLinkState(LinkState.Connected(welcome.name))
        listener.onServerMessage(Protocol.ServerMessage.Welcome(welcome))
    }

    private fun scheduleReconnect() {
        if (!wantsConnection || reconnecting) return
        reconnecting = true
        attempt += 1
        listener.onLinkState(LinkState.Reconnecting(attempt))

        // 0.5, 1, 2, 4, 8, 10 s with ±20 % jitter, exactly as the protocol specifies.
        val base = min(10.0, 2.0.pow((attempt - 1).coerceAtLeast(0).toDouble()) * 0.5)
        val delay = (base * Random.nextDouble(0.8, 1.2) * 1000).toLong()

        main.postDelayed({
            reconnecting = false
            if (wantsConnection) open()
        }, delay)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        val executor = Executors.newSingleThreadScheduledExecutor()
        heartbeat = executor
        executor.scheduleAtFixedRate(
            { sendFrame(Protocol.ping()) },
            PING_INTERVAL_S,
            PING_INTERVAL_S,
            TimeUnit.SECONDS,
        )
    }

    private fun stopHeartbeat() {
        heartbeat?.shutdownNow()
        heartbeat = null
    }

    private fun closeSocket() {
        socket?.let { client ->
            runCatching { client.close(1000, "bye") }
            runCatching { client.connectionLostTimeout = 0 }
        }
        socket = null
    }

    // ---- Sending ------------------------------------------------------------------------------

    fun send(frame: String?) {
        if (frame != null) sendFrame(frame)
    }

    private fun sendFrame(frame: String) {
        val client = socket ?: return
        runCatching { client.send(frame) }
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private fun isOpen(): Boolean = socket?.isOpen == true

    private fun friendly(code: String): String = when (code) {
        "auth" -> "This phone is no longer paired with that TV."
        "version" -> "Update Couch Remote to talk to this TV."
        "pin" -> "That code was not accepted."
        "not-allowed" -> "That app is not on the TV's home screen."
        else -> "Something went wrong. Try reconnecting."
    }

    private companion object {
        const val PING_INTERVAL_S = 15L
    }
}
