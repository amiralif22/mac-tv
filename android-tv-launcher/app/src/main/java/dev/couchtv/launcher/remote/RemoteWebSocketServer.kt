package dev.couchtv.launcher.remote

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The TV side of the remote: a WebSocket server that pairs phones, receives input and pushes state.
 *
 * Contract: `shared/remote-protocol.md`. Everything about *what to do* with input is delegated to the
 * [RemoteInputSink] the launcher registers, so this class stays about the connection — handshake,
 * pairing, limits and fan-out — and knows nothing about Compose.
 *
 * Lifecycle: construct, [start], and call [stop] when the remote is turned off. All sockets are closed
 * and the executor shut down; the server is not reusable afterwards, which keeps state handling
 * simple.
 */
class RemoteWebSocketServer(
    private val port: Int,
    private val deviceId: String,
    private val pairing: PairingStore,
    private val tvName: String,
    private val tvModel: String,
    private val screenSize: () -> Pair<Int, Int>,
    private val sink: () -> RemoteInputSink?,
    private val stateProvider: () -> RemoteState,
    private val appsProvider: () -> List<RemoteApp>,
    private val onClientsChanged: (Int) -> Unit,
) : WebSocketServer(InetSocketAddress(port)) {

    private class Client {
        var device: PairingStore.Device? = null
        var paired = false
        var connectedAt: Long = System.currentTimeMillis()
        var windowStart: Long = System.currentTimeMillis()
        var windowCount: Int = 0
        var dropped: Int = 0
        var lastPointerAt: Long = 0L
    }

    private val clients = ConcurrentHashMap<WebSocket, Client>()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "couch-remote-state").apply { isDaemon = true }
    }
    @Volatile
    private var statePending = false
    @Volatile
    private var lastStateAt = 0L

    init {
        setReuseAddr(true)
        setConnectionLostTimeout(45)
        setTcpNoDelay(true)
    }

    override fun onStart() {
        // Nothing to announce here; RemoteHub owns discovery. Reaching this means the port is bound.
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        if (clients.size >= MAX_CLIENTS) {
            conn.send(Protocol.error("busy", "This TV already has $MAX_CLIENTS remotes connected."))
            conn.close(1008, "busy")
            return
        }
        val client = Client()
        clients[conn] = client
        onClientsChanged(clients.size)
        // A client that never says hello is not allowed to sit on a socket.
        scheduler.schedule({
            if (!client.paired && System.currentTimeMillis() - client.connectedAt > HANDSHAKE_TIMEOUT_MS) {
                conn.close(1008, "handshake timeout")
            }
        }, HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val client = clients[conn] ?: return
        if (message.length > MAX_MESSAGE_CHARS) {
            conn.close(1009, "too large")
            return
        }
        if (!allow(client)) {
            conn.close(1008, "rate")
            return
        }

        when (val decoded = Protocol.decode(message)) {
            null -> Unit // Unknown types are ignored, by contract.
            is Protocol.ClientMessage.Hello -> handleHello(conn, client, decoded)
            is Protocol.ClientMessage.Pair -> handlePair(conn, client, decoded)
            is Protocol.ClientMessage.Ping -> conn.send(Protocol.pong(decoded.ts, System.currentTimeMillis()))
            else -> {
                if (!client.paired) {
                    conn.send(Protocol.error("auth", "Pair this remote first."))
                    return
                }
                deliver(decoded)
            }
        }
    }

    private fun handleHello(conn: WebSocket, client: Client, hello: Protocol.ClientMessage.Hello) {
        if (hello.version > Protocol.VERSION) {
            conn.send(Protocol.error("version", "This TV speaks remote protocol v${Protocol.VERSION}."))
            conn.close(1008, "version")
            return
        }
        val device = hello.token?.let(pairing::deviceForToken)
        if (device == null) {
            client.paired = false
            conn.send(
                Protocol.pairRequired(
                    deviceId = deviceId,
                    tvName = tvName,
                    pinLength = Pairing.CODE_DIGITS,
                )
            )
            return
        }
        // Sliding expiry: each successful hello mints a fresh token and retires the old one.
        val token = pairing.rotateToken(device.id)
        if (token == null) {
            conn.send(Protocol.error("auth", "Unknown or expired token"))
            return
        }

        client.device = device
        client.paired = true
        val (width, height) = screenSize()
        conn.send(Protocol.welcome(deviceId, token, tvName, tvModel, width, height))
        conn.send(Protocol.state(stateProvider()))
        conn.send(Protocol.apps(appsProvider()))
        onClientsChanged(activeClients())
    }

    private fun handlePair(conn: WebSocket, client: Client, pair: Protocol.ClientMessage.Pair) {
        if (pairing.isBlocked()) {
            conn.send(Protocol.error("busy", "Too many attempts. Try again in ten minutes."))
            return
        }
        val token = pairing.pairWithPin(pair.pin, pair.id, pair.name)
        if (token == null) {
            conn.send(
                Protocol.error(
                    code = "pin",
                    message = "That code didn't work.",
                    attempts = pairing.remainingAttempts(),
                )
            )
            return
        }
        val device = pairing.deviceForToken(token)
        client.device = device
        client.paired = true
        val (width, height) = screenSize()
        conn.send(Protocol.paired(deviceId, token, tvName, tvModel, width, height))
        conn.send(Protocol.state(stateProvider()))
        conn.send(Protocol.apps(appsProvider()))
        onClientsChanged(activeClients())
    }

    /** Routes one input message to the launcher, then to the system for anything it did not take. */
    private fun deliver(message: Protocol.ClientMessage) {
        val target = sink() ?: return
        when (message) {
            is Protocol.ClientMessage.Key -> target.onKey(message.key, message.hold)
            is Protocol.ClientMessage.Pointer -> target.onPointer(message.dx, message.dy)
            is Protocol.ClientMessage.PointerBatch -> message.samples.forEach { target.onPointer(it.dx, it.dy) }
            Protocol.ClientMessage.Click -> target.onClick()
            Protocol.ClientMessage.LongPress -> target.onLongPress()
            is Protocol.ClientMessage.Scroll -> target.onScroll(message.dy)
            is Protocol.ClientMessage.Text -> target.onText(message.value)
            is Protocol.ClientMessage.Backspace -> target.onBackspace(message.count)
            Protocol.ClientMessage.Submit -> target.onKey(Protocol.RemoteKey.OK, hold = false)
            is Protocol.ClientMessage.Voice -> {
                // Transcription happens on the phone; the TV treats it as typed text plus Enter.
                target.onText(message.text)
            }

            is Protocol.ClientMessage.Launch -> target.onLaunch(message.packageName)
            is Protocol.ClientMessage.Command -> target.onCommand(message.action, message.arg)
            else -> Unit
        }
    }

    /** 240 messages a second per client: enough for a 60 Hz pointer plus typing, no more. */
    private fun allow(client: Client): Boolean {
        val now = System.currentTimeMillis()
        if (now - client.windowStart >= 1000) {
            if (client.dropped > 0) client.dropped = 0
            client.windowStart = now
            client.windowCount = 0
        }
        client.windowCount++
        if (client.windowCount > MAX_MESSAGES_PER_SECOND) {
            client.dropped++
            return false
        }
        return true
    }

    fun activeClients(): Int = clients.count { it.value.paired }

    val clientCount: Int get() = clients.size

    fun broadcastToPaired(json: String) {
        clients.forEach { (connection, client) ->
            if (client.paired && connection.isOpen) {
                runCatching { connection.send(json) }
            }
        }
    }

    /** Coalesced to at most 10 Hz: a state storm must never become a network storm. */
    fun broadcastState() {
        val now = System.currentTimeMillis()
        if (now - lastStateAt < STATE_MIN_INTERVAL_MS) {
            if (!statePending) {
                statePending = true
                scheduler.schedule({
                    statePending = false
                    sendStateNow()
                }, STATE_MIN_INTERVAL_MS - (now - lastStateAt), TimeUnit.MILLISECONDS)
            }
            return
        }
        sendStateNow()
    }

    private fun sendStateNow() {
        lastStateAt = System.currentTimeMillis()
        broadcastToPaired(Protocol.state(stateProvider()))
    }

    fun broadcastApps() {
        broadcastToPaired(Protocol.apps(appsProvider()))
    }

    /** Tells a revoked device its token is dead, then closes its sockets. */
    fun disconnectDevice(deviceId: String) {
        clients.forEach { (connection, client) ->
            if (client.device?.id == deviceId) {
                runCatching { connection.send(Protocol.event("paired-elsewhere")) }
                runCatching { connection.close(1008, "revoked") }
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients.remove(conn)
        onClientsChanged(activeClients())
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        // A broken socket is expected (phones sleep, Wi-Fi drops). Never escalate to a crash.
        conn?.let { clients.remove(it) }
    }

    fun shutdown() {
        runCatching { connections.forEach { it.close(1001, "TV closing") } }
        runCatching { stop(1000) }
        scheduler.shutdownNow()
        clients.clear()
    }

    private companion object {
        const val MAX_CLIENTS = 8
        const val MAX_MESSAGE_CHARS = 64 * 1024
        const val MAX_MESSAGES_PER_SECOND = 240
        const val HANDSHAKE_TIMEOUT_MS = 5_000L
        const val STATE_MIN_INTERVAL_MS = 100L
    }
}
