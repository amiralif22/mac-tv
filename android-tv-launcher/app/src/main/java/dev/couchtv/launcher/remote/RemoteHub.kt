package dev.couchtv.launcher.remote

import android.content.Context
import android.os.Build
import dev.couchtv.launcher.apk.LocalHttpServer
import dev.couchtv.launcher.util.NetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Owns the remote server, the HTTP surface and discovery, and keeps them alive across activity
 * recreation.
 *
 * The launcher registers two things here — what the TV is doing ([stateProvider]) and what the user
 * selected ([appsProvider]) — plus the sink that receives input. That is the entire coupling between
 * the UI and the network: everything else (ports, pairing, mDNS, limits) lives behind this object.
 */
object RemoteHub {

    const val DEFAULT_WS_PORT = 8765
    const val DEFAULT_HTTP_PORT = 8770

    private const val PREFS = "remote_hub"
    private const val KEY_DEVICE_ID = "device_id"

    val running = MutableStateFlow(false)
    val clientCount: StateFlow<Int> get() = clientCountState.asStateFlow()
    private val clientCountState = MutableStateFlow(0)

    /** The six digits currently on screen, refreshed every five minutes. */
    val pairingCode = MutableStateFlow("------")

    /** The WebSocket port actually in use (a busy port moves to the next one). */
    val wsPort = MutableStateFlow(0)
    val httpPort = MutableStateFlow(0)

    /** Set by the launcher's view model. */
    @Volatile
    var sink: RemoteInputSink? = null

    @Volatile
    var stateProvider: () -> RemoteState = { RemoteState() }

    @Volatile
    var appsProvider: () -> List<RemoteApp> = { emptyList() }

    @Volatile
    var tvName: String = "Couch TV"

    private var server: RemoteWebSocketServer? = null
    private var http: LocalHttpServer? = null
    private var advertiser: MdnsAdvertiser? = null
    private var pairing: PairingStore? = null
    private var appContext: Context? = null
    private var deviceId: String = ""

    fun deviceId(): String = deviceId

    /** Starts everything; safe to call twice. Returns true when the WebSocket server is listening. */
    fun start(context: Context, requestedWsPort: Int = DEFAULT_WS_PORT, requestedHttpPort: Int = DEFAULT_HTTP_PORT): Boolean {
        val application = context.applicationContext
        appContext = application
        if (running.value && server != null) return true

        val store = pairing ?: PairingStore(application).also { pairing = it }
        deviceId = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .let { prefs ->
                prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
                    prefs.edit().putString(KEY_DEVICE_ID, id).apply()
                }
            }
        tvName = runCatching {
            @Suppress("DEPRECATION")
            (application.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)
                ?.networkOperatorName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: defaultName(application)

        // The WebSocket port: the requested one, then the next three. A TV box sometimes has a
        // service on 8765 already; the remote app discovers whichever port mDNS advertises.
        var bound: RemoteWebSocketServer? = null
        var lastError: Throwable? = null
        for (candidate in requestedWsPort until requestedWsPort + 4) {
            val instance = RemoteWebSocketServer(
                port = candidate,
                deviceId = deviceId,
                pairing = store,
                tvName = tvName,
                tvModel = if (isTv(application)) "Android TV" else "Android",
                screenSize = {
                    val metrics = application.resources.displayMetrics
                    metrics.widthPixels to metrics.heightPixels
                },
                sink = { sink },
                stateProvider = { stateProvider() },
                appsProvider = { appsProvider() },
                onClientsChanged = { count -> clientCountState.value = count },
            )
            val started = runCatching {
                instance.start()
                true
            }.getOrElse { error ->
                lastError = error
                runCatching { instance.shutdown() }
                false
            }
            if (started) {
                bound = instance
                wsPort.value = candidate
                break
            }
        }
        if (bound == null) {
            android.util.Log.w("CouchTv/remote", "Couldn't start the remote server: ${lastError?.message}")
            return false
        }
        server = bound

        // The HTTP surface (upload page, icons, info) is separate: it must never be able to block a
        // remote's keystrokes behind a slow file upload.
        val httpServer = LocalHttpServer(
            context = application,
            port = requestedHttpPort,
            onUploaded = { file -> UploadBus.publish(file) },
            infoProvider = { infoJson(application) },
            iconProvider = { packageName -> sink?.iconFor(packageName) },
        )
        if (httpServer.start()) {
            http = httpServer
            httpPort.value = requestedHttpPort
        }

        val mdns = MdnsAdvertiser(application)
        mdns.start(
            port = wsPort.value,
            name = "Couch TV · $tvName",
            txt = mapOf(
                "id" to deviceId,
                "name" to tvName,
                "v" to Protocol.VERSION.toString(),
                "pair" to if (store.currentCode().isExpired(System.currentTimeMillis())) "0" else "1",
                "model" to if (isTv(application)) "Android TV" else "Android",
                "path" to "/remote",
            ),
        )
        advertiser = mdns

        pairingCode.value = store.currentCode().value
        running.value = true
        return true
    }

    fun stop() {
        runCatching { server?.shutdown() }
        runCatching { http?.stop() }
        runCatching { advertiser?.stop() }
        server = null
        http = null
        advertiser = null
        clientCountState.value = 0
        running.value = false
    }

    fun pairingStore(): PairingStore? = pairing

    /** A new six-digit code (Settings › Remote › New code). */
    fun newPairingCode(): String {
        val code = pairing?.forceNewCode()?.value ?: "------"
        pairingCode.value = code
        return code
    }

    fun refreshPairingCode() {
        pairingCode.value = pairing?.currentCode()?.value ?: "------"
    }

    fun broadcastState() {
        server?.broadcastState()
    }

    fun broadcastApps() {
        server?.broadcastApps()
    }

    fun broadcastToast(text: String) {
        server?.broadcastToPaired(Protocol.toast(text))
    }

    fun revoke(deviceId: String) {
        pairing?.revoke(deviceId)
        server?.disconnectDevice(deviceId)
    }

    fun httpUrl(): String? {
        val address = NetworkInfo.localAddress() ?: return null
        return "http://$address:${httpPort.value}"
    }

    fun wsAddress(): String? {
        val address = NetworkInfo.localAddress() ?: return null
        return "$address:${wsPort.value}"
    }

    private fun infoJson(context: Context): String = buildString {
        append("{\"id\":\"").append(deviceId).append("\",")
        append("\"name\":\"").append(tvName.replace("\"", "")).append("\",")
        append("\"model\":\"").append(if (isTv(context)) "Android TV" else "Android").append("\",")
        append("\"v\":").append(Protocol.VERSION).append(',')
        append("\"port\":").append(wsPort.value).append(',')
        append("\"pin\":").append(pairingCode.value != "------")
        append('}')
    }

    private fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            (Build.VERSION.SDK_INT >= 21 && context.packageManager.hasSystemFeature("android.hardware.type.television"))

    private fun defaultName(context: Context): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "TV"
        return model.replaceFirstChar { it.uppercase() }
    }
}

/**
 * A one-way channel from the HTTP upload server to the launcher UI: the server runs away from
 * Compose, so it publishes a file and the view model picks it up on its next tick.
 */
object UploadBus {
    private val _pending = MutableStateFlow<java.io.File?>(null)
    val pending: StateFlow<java.io.File?> = _pending.asStateFlow()

    fun publish(file: java.io.File) {
        _pending.value = file
    }

    fun consume(): java.io.File? = _pending.value?.also { _pending.value = null }
}
